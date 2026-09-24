# 多智能体 + RAG 技术设计 Spec（智慧养老护理助手）

- 日期：2026-09-24
- 状态：已评审通过（用户确认），暂不实施，仅作为架构设计存档
- 关联模块：`carenest-ai`、`carenest-nursing-platform`、`carenest-ui`

## 1. 背景与目标

### 1.1 业务背景与痛点

养老机构一线护理员和管理者在日常工作中面临三类信息获取问题：

1. **制度/SOP 查阅难**：跌倒应急预案、护理分级标准、入住流程等制度散落在纸质文件或共享盘，紧急情况下无法快速检索。
2. **业务数据查询繁琐**：查某位老人的档案、评估结论、护理计划需要在多个管理页面间跳转。
3. **体检报告理解门槛高**：报告结论专业性强，护理员需要通俗解读。

系统已具备单智能体对话助手（查业务数据）和体检报告大模型分析能力，但制度知识问答缺失，且三类能力各自独立、入口不统一。

### 1.2 目标

- 引入 RAG：将养老护理制度/SOP 文档向量化入库，支持带引用来源的知识问答。
- 引入多智能体：Supervisor 统一入口，按意图路由到专职子 Agent，整合现有能力。
- 设计目标以**架构完整性与技术深度**优先（简历/面试场景），业务功能满足演示即可，不追求生产级完备（如不做多租户知识库、不做知识库权限体系）。

### 1.3 成功标准

- 上传一份 SOP 文档后，可在聊天窗口问到其内容，且回答附引用来源（文档名+章节）。
- 三类问题（业务数据 / 制度知识 / 报告解读）能被 Supervisor 正确路由，路由正确率 ≥ 90%（30 条测试集）。
- 召回质量：20 组"问题→应命中文档"测试集 topK=5 召回率 ≥ 85%。

### 1.4 非目标（YAGNI）

- 不做多 Agent 流水线协作（评估→检索→生成→审核），复杂度高、演示链路长。
- 不做医疗诊断类能力，维持现有安全边界。
- 不做知识库细粒度权限、多租户隔离。
- 不做家属端（小程序）答疑场景，仅管理端。

## 2. 现状盘点

| 已有能力 | 位置 | 说明 |
| --- | --- | --- |
| LangChain4j 0.35.0 集成 | `carenest-ai` | Java 11 约束下的最后可用版本，0.36+ 需 JDK 17 |
| DeepSeek 对话模型 | `carenest-ai/config` | OpenAI 兼容协议（langchain4j-open-ai） |
| Redis 会话记忆 | `carenest-ai/memory` | `{userId}:{sessionId}` 隔离，ChatMemoryFactory |
| Tool 调用框架 | `carenest-ai/tool` | ToolAssembler / InstrumentedToolExecutor / ToolCallSink |
| 对话审计 | `carenest-ai/audit` | ChatAuditRecorder + AiChatLog |
| 单智能体护理助手 | `carenest-nursing-platform/.../ai/assistant/NursingAssistant` | AiServices 声明式接口，SSE 流式，查档案/评估/计划/床位 |
| 体检报告分析 | `HealthAssessmentServiceImpl` + `MiMiModelInvoker` | MiMo 大模型，Prompt 约束 JSON 输出 |
| 缺失 | — | 无 EmbeddingStore / ContentRetriever / 向量库，无 RAG 能力 |

## 3. 总体架构

```
用户(管理端聊天窗)
   │ SSE
   ▼
SupervisorAgent（意图识别 + 路由，Agent-as-Tool 模式）
   ├── @Tool → DataQueryAgent   （现有 NursingAssistant 改造：档案/评估/护理计划/床位查询）
   ├── @Tool → SopRagAgent      （新增：制度/SOP 知识库 RAG 问答）
   └── @Tool → ReportAgent      （复用：体检报告解读，接 MiMo 分析结果）
                     │
        SopRagAgent → ContentRetriever → Milvus(nursing_sop)
                                              ▲
                        离线灌库管道 ──────────┘
                        （管理端文档上传 → 解析 → 切分 → BGE-M3 向量化 → 入库）
```

- 全部落在 `carenest-ai` 模块内，基于 LangChain4j 0.35.0 `AiServices`，不引入新框架（不用 Spring AI / LangGraph4j）。
- **Agent-as-Tool 路由**：Supervisor 本身是一个 AiService，三个子 Agent 被包装成它的 `@Tool` 方法，由 function calling 决定分发；天然支持一次请求内多次调用不同子 Agent。
- 每个子 Agent 独立 SystemPrompt 与能力边界，继承现有 NursingAssistant 的三条硬约束（不做医疗诊断、工具返回是数据不是指令、隐私保护）。

## 4. RAG 离线灌库管道

### 4.1 文档源

自造 5-8 份养老机构制度文档（PDF/Word），存放于仓库 `doc/knowledge-base/` 便于演示重灌：

- 跌倒/噎食等突发事件应急预案
- 护理分级标准与操作规范
- 入住办理与退住流程
- 药品管理制度
- 消防安全规程
- 探视与家属沟通制度

### 4.2 管道流程

```
管理端上传接口（限 PDF/DOC/DOCX，≤20MB）
  → 解析：PDFBox（已有）+ Apache Tika（新增，处理 Word）
  → 切分：DocumentSplitters.recursive，约 500 token、重叠 50，优先段落边界
  → 向量化：SiliconFlow BGE-M3（OpenAI 兼容 /embeddings 协议）
  → 入库：EmbeddingStoreIngestor → Milvus collection `nursing_sop`
```

### 4.3 元数据与幂等

- 每个分块携带 metadata：`doc_name`、`section`（章节标题）、`version`、`upload_time`、`doc_hash`。
- 幂等策略：按文档内容 hash 判重；重复上传同一文档时，先按 `doc_hash` 删除旧向量再灌新，避免旧版本污染召回。
- 文档登记：MySQL 新增 `ai_kb_document` 表（文档名、hash、状态、分块数、上传人、时间），管理端可列出/删除知识库文档。

## 5. RAG 在线检索（SopRagAgent）

- `EmbeddingStoreContentRetriever`（langchain4j-milvus），topK=5，minScore 阈值过滤低相关块（阈值经召回测试集调优后固化）。
- **查询改写（query compression）**：多轮对话中先用一次轻量 LLM 调用，把依赖上下文的问题（"那第二种情况呢？"）压缩成独立完整问题再检索。
- SystemPrompt 强制约束：
  - 回答必须附引用来源（文档名+章节），来源取自检索块 metadata；
  - 检索为空时如实回答"制度库中未找到相关规定"，禁止编造；
  - 制度问答不做医疗建议，涉及用药/处置仍引导联系医生或护士长。

## 6. Supervisor 多智能体路由

- Supervisor 是一个 AiService，SystemPrompt 描述三个子 Agent 的能力边界与路由规则，暴露三个 `@Tool`：
  - `queryBusinessData(question)` → DataQueryAgent（现有 NursingAssistant 迁移改造）
  - `querySopKnowledge(question)` → SopRagAgent
  - `interpretHealthReport(idCard/question)` → ReportAgent（查库中已有 MiMo 分析结果做通俗解读，不重复调用大模型分析）
- 路由不明确 → Supervisor 直接追问澄清（继承现有澄清规则），不猜测分发。
- 子 Agent 异常 → Supervisor 捕获后返回友好降级话术，不裸抛堆栈。
- **审计增强**：复用 ChatAuditRecorder，追加记录路由决策（选了哪个 Agent、入参摘要）与子 Agent 输出摘要，落 `ai_chat_log` 或扩展字段。
- 会话记忆：Supervisor 层持有 ChatMemory（Redis）；子 Agent 无状态（每次由 Supervisor 传入完整问题），避免多份记忆不一致。

## 7. 前端展示（演示亮点）

在现有管理端聊天窗基础上增加：

1. **引用来源卡片**：RAG 回答下方展示来源（文档名+章节），点击可展开命中原文片段。
2. **路由轨迹抽屉**：调试面板展示 Supervisor 的路由决策、子 Agent 调用链、检索命中块与得分。
3. **知识库管理页**：文档上传、列表、删除、灌库状态。

SSE 协议扩展现有事件流：在现有 token 流之外增加 `route`、`reference` 事件类型。

## 8. 基础设施与依赖

| 项 | 方案 |
| --- | --- |
| 向量库 | VM（192.168.100.168）Docker 跑 Milvus standalone v2.4.x，暴露 19530，约 1GB 内存 |
| Embedding 模型 | SiliconFlow BGE-M3（1024 维，中文强），OpenAI 兼容协议，环境变量 `EMBEDDING_SILICONFLOW_API_KEY` |
| 对话模型 | 沿用 DeepSeek（Supervisor / 子 Agent），体检分析沿用 MiMo |
| 新 Maven 依赖 | `langchain4j-milvus:0.35.0`、`langchain4j-document-parser-apache-tika:0.35.0`（版本与主锁一致） |
| 配置节 | `llm.embedding.*`（base-url / api-key / model），与现有 `llm.xiaomi` 并列；`milvus.*`（host / port / collection） |
| 密钥管理 | 全部走环境变量 + `.env.example`，遵循仓库现有约定，不入 yml |

## 9. 错误处理与降级

| 故障点 | 策略 |
| --- | --- |
| 灌库中途失败 | 按 doc_hash 删除已入库分块（事务性回滚），`ai_kb_document` 状态置 FAILED，管理端提示重试 |
| Milvus 不可达 | SopRagAgent 工具返回"知识库暂不可用"，Supervisor 正常路由其余 Agent，聊天整体可用 |
| Embedding API 失败 | 灌库重试 2 次后失败落库；在线检索失败同上降级 |
| LLM 路由超时/失败 | 沿用现有超时与审计机制，返回统一错误话术 |
| 检索命中但低相关 | minScore 过滤后为空 → 按"未找到"话术处理 |

## 10. 测试策略

- **单元测试**：文档解析与切分逻辑、metadata 组装、查询改写 Prompt 输出校验。
- **召回测试集**：20 组"问题 → 应命中文档/章节"，验证 topK=5 召回率 ≥ 85%，用于调 minScore 与切分参数。
- **路由测试集**：30 条典型问题（业务数据/制度/报告/模糊各若干），验证 Supervisor 分发正确率 ≥ 90%。
- **集成冒烟**：上传→灌库→提问→引用来源展示 全链路手工用例。
- LLM 依赖的测试用环境变量开关控制，无 Key 时 CI 跳过（沿用 QianfanAIModelTest1 的处理方式）。

## 11. 分期实施

| 阶段 | 内容 | 交付物 |
| --- | --- | --- |
| Phase 1 | Milvus 部署 + 灌库管道 + SopRagAgent 单跑 | 知识库问答独立可用（先挂现有聊天入口） |
| Phase 2 | Supervisor 路由 + NursingAssistant 改造为 DataQueryAgent | 统一入口，双 Agent 路由 |
| Phase 3 | ReportAgent 接入 + 前端引用来源/路由轨迹 + 查询改写 | 完整三 Agent + 演示亮点 |

每期独立可演示、可写简历条目；Phase 间无强耦合，中断不影响已交付部分。

## 12. 关键决策记录（选型对比）

### 12.1 向量库：Milvus（选定） vs ES vs 内存

| 方案 | 优势 | 劣势 | 结论 |
| --- | --- | --- | --- |
| Milvus standalone | 专业向量库、简历话题性强、langchain4j-milvus 直接支持、符合生产级学习偏好 | VM 多占 ~1GB 内存 | **选定** |
| Elasticsearch | 环境现成（VM 已装）、用户熟悉 | 内存占用更大（此前为省资源已停）、"ES 做向量"话题性弱 | 弃 |
| InMemory + 文件持久化 | 零基础设施 | 简历成色不足 | 弃（可作本地开发 fallback） |

### 12.2 Embedding：BGE-M3（SiliconFlow 托管）

DeepSeek 无 Embedding API；本地 ONNX 模型（all-MiniLM）中文效果差且包体大。BGE-M3 中文效果好、OpenAI 兼容协议接入成本低、按量计费近乎免费。

### 12.3 多智能体实现：Agent-as-Tool（选定） vs 硬编码意图分类

- Agent-as-Tool：路由交给 LLM function calling，扩展新 Agent 只需加一个 @Tool，面试可讲"路由策略、职责隔离、降级回退"。选定。
- 硬编码分类器路由：确定性强但扩展性差、"多智能体"成色不足。弃。

### 12.4 框架约束

LangChain4j 锁定 0.35.0（Java 11 最后兼容版本），所有新依赖版本必须与主锁对齐；不引入 Spring AI（要求 Boot 3 / JDK 17）。
