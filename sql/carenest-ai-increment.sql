-- ============================================================
-- AI 对话审计表增量脚本
-- ------------------------------------------------------------
-- 用途：在已初始化的 carenest 库上单独补建 ai_chat_log 表。
--   全新部署（跑 carenest-dev06-init.sql）已包含该表，无需再执行本脚本。
-- 执行：mysql -uroot -p carenest < carenest-ai-increment.sql
--   或在客户端里对目标库直接运行。
-- 说明：医疗场景要求对话可追溯，本表与对话同轮落库，不做“先上线后补”。
-- ============================================================

USE `carenest`;

CREATE TABLE IF NOT EXISTS `ai_chat_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话id',
  `user_id` bigint DEFAULT NULL COMMENT '提问用户id',
  `dept_id` bigint DEFAULT NULL COMMENT '提问用户部门id',
  `user_role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '提问用户角色标识，多个用逗号分隔',
  `model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模型id',
  `user_input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '用户输入',
  `model_output` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '模型输出',
  `tool_calls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '工具调用链，JSON数组',
  `input_tokens` int DEFAULT NULL COMMENT '输入token数',
  `output_tokens` int DEFAULT NULL COMMENT '输出token数',
  `total_tokens` int DEFAULT NULL COMMENT '总token数',
  `cost_ms` bigint DEFAULT NULL COMMENT '本轮耗时（毫秒）',
  `success` tinyint DEFAULT NULL COMMENT '是否成功（0失败 1成功）',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '失败原因摘要',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_session` (`session_id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='AI对话审计日志';

-- ------------------------------------------------------------
-- 护理助手菜单与权限
--   挂在“服务管理”(menu_id=2000, path=nursing)目录下，前端组件 nursing/assistant/index。
--   权限标识 nursing:assistant:chat 与 AssistantController 上的 @PreAuthorize 对应。
--   现有 sys_menu 最大 menu_id=2052，此处显式使用 2053，避免与自增值冲突。
--   NOT EXISTS 守卫保证脚本可重复执行。
--   角色授权：超级管理员(role_id=1)默认可见全部菜单；其余角色请在“角色管理”界面勾选“护理助手”，
--   或按需自行 INSERT INTO sys_role_menu(role_id, menu_id) VALUES (<roleId>, 2053)。
-- ------------------------------------------------------------
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2053, '护理助手', 2000, 5, 'assistant', 'nursing/assistant/index', NULL, '',
       1, 0, 'C', '0', '0', 'nursing:assistant:chat', 'message',
       'admin', NOW(), '', NULL, '对话式护理助手菜单'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'nursing:assistant:chat');

-- ------------------------------------------------------------
-- 健康评估异步化：新增 AI 分析状态字段
--   analysis_status：0=分析中，1=分析成功，2=分析失败
--   analysis_error ：分析失败原因摘要（截断至 500 字）
--   背景：AI 分析体检报告耗时 10-30 秒，改为先落一条“分析中”记录立即返回，
--         由后台线程池跑完再回填，避免 HTTP 请求线程同步阻塞导致网关 504。
--   注意：health_assessment 为业务表，其建表 DDL 未纳入仓库，本段请在现有库执行一次即可
--         （MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，重复执行会报列已存在，属正常）。
-- ------------------------------------------------------------
ALTER TABLE `health_assessment`
  ADD COLUMN `analysis_status` tinyint DEFAULT 0 COMMENT 'AI分析状态(0分析中 1成功 2失败)' AFTER `system_score`,
  ADD COLUMN `analysis_error` varchar(500) DEFAULT NULL COMMENT 'AI分析失败原因' AFTER `analysis_status`;
