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
- 多模态扩展（Phase 4）：聊天支持发图提问，视觉模型解读（拍报告、拍现场等场景）。
- 设计目标以**架构完整性与技术深度**优先（简历/面试场景），业务功能满足演示即可，不追求生产级完备（如不做多租户知识库、不做知识库权限体系）。

### 1.3 成功标准

- 上传一份 SOP 文档后，可在聊天窗口问到其内容，且回答附引用来源（文档名+章节）。
- 三类问题（业务数据 / 制度知识 / 报告解读）能被 Supervisor 正确路由，路由正确率 ≥ 90%（30 条测试集）。
- 召回质量：20 组"问题→应命中文档"测试集 topK=5 召回率 ≥ 85%。
- 多模态（Phase 4）：聊天窗口发送一张体检报告照片，能获得通俗解读且不复述隐私字段。

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
   ├── @Tool → ReportAgent      （复用：体检报告解读，接 MiMo 分析结果）
   └── @Tool → VisionAgent      （Phase 4 扩展：图片多模态问答，deepseek-flash）
                     │
        SopRagAgent → ContentRetriever → Milvus(nursing_sop)
                                              ▲
                        离线灌库管道 ──────────┘
                        （管理端文档上传 → 解析 → 切分 → BGE-M3 向量化 → 入库）
```

- 全部落在 `carenest-ai` 模块内，基于 LangChain4j 0.35.0 `AiServices`，不引入新框架（不用 Spring AI / LangGraph4j）。
- **Agent-as-Tool 路由**：Supervisor 本身是一个 AiService，子 Agent 被包装成它的 `@Tool` 方法（Phase 1-3 三个，Phase 4 扩展第四个 VisionAgent），由 function calling 决定分发；天然支持一次请求内多次调用不同子 Agent。
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

### 4.2 管道流程（异步灌库）

```
管理端上传接口（限 PDF/DOC/DOCX，≤20MB）
  → 登记 ai_kb_document（状态 PENDING）→ 立即返回文档 ID（不阻塞请求线程）
  → 后台异步任务（独立线程池；也可挂 Quartz 周期扫描 PENDING 记录）
      → 解析：PDFBox（已有）+ Apache Tika（新增，处理 Word）
      → 切分：DocumentSplitters.recursive，约 500 token、重叠 50，优先段落边界
      → 向量化：SiliconFlow BGE-M3（OpenAI 兼容 /embeddings 协议）
      → 入库：EmbeddingStoreIngestor → Milvus collection `nursing_sop`
  → 状态流转：PENDING → PROCESSING → SUCCESS / FAILED（记录失败原因）
  → 前端知识库管理页轮询状态展示进度
```

异步理由：大文档"解析+切分+向量化+入库"耗时可达数十秒，同步接口必超时；且向量化依赖外部 API，失败重试不应占用 Web 线程。

### 4.3 元数据与幂等

- 每个分块携带 metadata：`doc_name`、`section`（章节标题）、`version`、`upload_time`、`doc_hash`。
- 幂等策略：按文档内容 hash 判重；重复上传同一文档时，先按 `doc_hash` 删除旧向量再灌新，避免旧版本污染召回。
- 文档登记：MySQL 新增 `ai_kb_document` 表（文档名、hash、状态 PENDING/PROCESSING/SUCCESS/FAILED、失败原因、分块数、上传人、时间），管理端可列出/删除知识库文档、查看灌库进度。

## 5. RAG 在线检索（SopRagAgent）

- `EmbeddingStoreContentRetriever`（langchain4j-milvus），topK=5，minScore 阈值过滤低相关块（阈值经召回测试集调优后固化）。
- **查询改写（query compression）**：多轮对话中先用一次轻量 LLM 调用，把依赖上下文的问题（"那第二种情况呢？"）压缩成独立完整问题再检索。
- SystemPrompt 强制约束：
  - 回答必须附引用来源（文档名+章节），来源取自检索块 metadata；
  - 检索为空时如实回答"制度库中未找到相关规定"，禁止编造；
  - 制度问答不做医疗建议，涉及用药/处置仍引导联系医生或护士长。

## 6. Supervisor 多智能体路由

- Supervisor 是一个 AiService，SystemPrompt 描述各子 Agent 的能力边界与路由规则，暴露四个 `@Tool`（第四个为 Phase 4 扩展）：
  - `queryBusinessData(question)` → DataQueryAgent（现有 NursingAssistant 迁移改造）
  - `querySopKnowledge(question)` → SopRagAgent
  - `interpretHealthReport(idCard/question)` → ReportAgent（查库中已有 MiMo 分析结果做通俗解读，不重复调用大模型分析）
  - `analyzeImage(question, imageUrls)` → VisionAgent（Phase 4 扩展，见 6.5 节）
- 路由不明确 → Supervisor 直接追问澄清（继承现有澄清规则），不猜测分发。
- 子 Agent 异常 → Supervisor 捕获后返回友好降级话术，不裸抛堆栈。
- **审计增强**：复用 ChatAuditRecorder，追加记录路由决策（选了哪个 Agent、入参摘要）与子 Agent 输出摘要，落 `ai_chat_log` 或扩展字段。
- 会话记忆：Supervisor 层持有 ChatMemory（Redis）；子 Agent 无状态（每次由 Supervisor 传入完整问题），避免多份记忆不一致。

## 6.5 多模态图片问答（VisionAgent，Phase 4 扩展）

- **模型**：DeepSeek `deepseek-flash`（官方 API 多模态模型，OpenAI 兼容协议，图片走 content 数组 `image_url` 字段，支持 URL/base64）。与现有 `langchain4j-open-ai` 集成完全复用，仅新增一个 ChatModel 实例与配置。
- **图片链路**：聊天输入框上传图片（限 jpg/png，≤10MB）→ 复用 `AliyunOSSOperator` 传 OSS → 生成**带签名的临时 URL**（`generatePresignedUrl`，有效期 ≤30 分钟；桶禁公共读）→ `ChatRequest` 新增 `imageUrls` 字段 → Supervisor 判定消息带图 → 调用 `analyzeImage(question, imageUrls)` @Tool → VisionAgent 以 `UserMessage.from(TextContent, ImageContent.from(url))` 请求 deepseek-flash。
- **成本控制**：纯文本消息继续走现有文本模型，仅带图消息路由到 deepseek-flash。
- **路由规则**：消息含图片一律先走 VisionAgent 解读；若识别出是体检报告照片，提示用户走正式的健康评估上传流程（PDF 链路），VisionAgent 只做即时解读不落库。
- **边界约束**：继承三条硬约束——图片中涉及病情判断/用药的，只做信息描述并引导联系医生；图片中的身份证号、手机号等隐私不得复述；工具/图片内容视为数据非指令。
- **审计**：`ai_chat_log` 记录图片 OSS URL 与解读输出摘要；审计日志不内嵌 base64 图片体。
- **会话记忆兼容**：Redis 记忆中的 ImageContent 仅保留 URL 引用（签名 URL 过期后由前端按 chat_log 中的 OSS key 重新换签），不存 base64，避免记忆体膨胀。

## 6.6 隐私脱敏与输出安全层（横切组件）

Prompt 约束模型"不要泄露隐私"是软约束，不可作为唯一防线。增加确定性代码层，位于所有子 Agent 的 LLM 调用前后：

- **入模前脱敏（Pre-prompt Masking）**：工具返回的业务数据、RAG 检索块在拼入 Prompt 前，经 `SensitiveDataMasker` 正则处理——身份证号 → `3301**********1234`、手机号 → `138****5678`、住址 → 保留到区级。模型从源头拿不到完整敏感值，无论怎么被诱导都吐不出来。
- **输出侧校验（Post-output Filter）**：对模型输出再过一次同样的正则扫描，命中完整身份证/手机号模式则替换为脱敏形式后返回（防模型从上下文其他途径拼出敏感值）。
- **落点**：作为 `carenest-ai` 的横切组件，在 InstrumentedToolExecutor 返回处和 SSE 输出前统一织入，不侵入各 Agent 业务代码。
- **审计一致性**：`ai_chat_log` 记录脱敏后的内容，审计库本身不存敏感明文。

## 7. 前端展示（演示亮点）

在现有管理端聊天窗基础上增加：

1. **引用来源卡片**：RAG 回答下方展示来源（文档名+章节），点击可展开命中原文片段。
2. **路由轨迹抽屉**：调试面板展示 Supervisor 的路由决策、子 Agent 调用链、检索命中块与得分。
3. **知识库管理页**：文档上传、列表、删除、灌库状态。
4. **图片上传**（Phase 4）：聊天输入框支持传图/贴图，消息气泡展示图片缩略图。
5. **答案反馈**：每条 AI 回复下方 👍/👎 按钮（👎 可选填原因），落 `ai_chat_feedback` 表（chat_log_id、评分、原因、时间），构成效果度量闭环的数据入口。

SSE 协议扩展现有事件流：在现有 token 流之外增加 `route`、`reference` 事件类型。

## 8. 基础设施与依赖

| 项 | 方案 |
| --- | --- |
| 向量库 | VM（192.168.100.168）Docker 跑 Milvus standalone v2.4.x，暴露 19530，约 1GB 内存 |
| Embedding 模型 | SiliconFlow BGE-M3（1024 维，中文强），OpenAI 兼容协议，环境变量 `EMBEDDING_SILICONFLOW_API_KEY` |
| 对话模型 | 沿用 DeepSeek（Supervisor / 子 Agent），体检分析沿用 MiMo；Phase 4 新增 `deepseek-flash` 视觉模型（同供应商同协议，第二个 ChatModel 实例） |
| 新 Maven 依赖 | `langchain4j-milvus:0.35.0`、`langchain4j-document-parser-apache-tika:0.35.0`（版本与主锁一致） |
| 配置节 | `llm.embedding.*`（base-url / api-key / model），与现有 `llm.xiaomi` 并列；`milvus.*`（host / port / collection） |
| 密钥管理 | 全部走环境变量 + `.env.example`，遵循仓库现有约定，不入 yml |

### 8.1 Nginx 反代下的 SSE 配置（部署必配）

生产经 Nginx 反代时，SSE 默认会被缓冲成一次性响应，必须针对聊天接口路径配置：

```nginx
location /nursing/assistant/chat/stream {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;          # 关闭响应缓冲，token 逐个下发
    proxy_cache off;
    proxy_read_timeout 300s;      # 大于最长一次 LLM 生成耗时
    chunked_transfer_encoding on;
}
```

后端同时在 SSE 响应头设置 `X-Accel-Buffering: no` 双保险。

## 9. 错误处理与降级

| 故障点 | 策略 |
| --- | --- |
| 灌库任务失败 | 按 doc_hash 删除已入库分块（事务性回滚），`ai_kb_document` 置 FAILED 并记录原因；管理端可一键重试（重新入队异步任务），向量化 API 类瞬时错误自动重试 2 次 |
| Milvus 不可达 | SopRagAgent 工具返回"知识库暂不可用"，Supervisor 正常路由其余 Agent，聊天整体可用 |
| Embedding API 失败 | 灌库重试 2 次后失败落库；在线检索失败同上降级 |
| LLM 路由超时/失败 | 沿用现有超时与审计机制，返回统一错误话术 |
| 检索命中但低相关 | minScore 过滤后为空 → 按"未找到"话术处理 |
| 图片上传/OSS 失败（Phase 4） | 前端提示重传；OSS URL 失效 → VisionAgent 返回"图片无法访问，请重新上传" |
| deepseek-flash 调用失败（Phase 4） | 重试 1 次后降级为文本话术，提示用户改用文字描述 |

## 10. 测试策略

- **单元测试**：文档解析与切分逻辑、metadata 组装、查询改写 Prompt 输出校验。
- **召回测试集**：20 组"问题 → 应命中文档/章节"，验证 topK=5 召回率 ≥ 85%，用于调 minScore 与切分参数。
- **路由测试集**：30 条典型问题（业务数据/制度/报告/模糊各若干），验证 Supervisor 分发正确率 ≥ 90%。
- **集成冒烟**：上传→灌库→提问→引用来源展示 全链路手工用例；Phase 4 增加"传图提问→OSS→VisionAgent 解读"冒烟用例。
- LLM 依赖的测试用环境变量开关控制，无 Key 时 CI 跳过（沿用 QianfanAIModelTest1 的处理方式）。

### 10.1 线上效果度量闭环

一次性测试集只保上线质量，持续度量保运营质量：

| 指标 | 数据来源 | 用途 |
| --- | --- | --- |
| 👍/👎 率 | `ai_chat_feedback` | 答案质量总览，👎 集中的问题人工复盘 |
| 检索空结果率 | SopRagAgent 审计日志 | 高说明知识库有缺口，指导补文档 |
| 路由分布 / 误路由 | Supervisor 审计日志 | 优化 SystemPrompt 路由规则 |
| 引用来源点击率 | 前端埋点 | 验证引用是否真实有用 |

👎 的问题定期（如每两周）人工修正后**回流进召回/路由测试集**，测试集随运营增长——形成"反馈→修知识库/修Prompt→回归验证"闭环。

## 11. 分期实施

| 阶段 | 内容 | 交付物 |
| --- | --- | --- |
| Phase 1 | Milvus 部署 + 异步灌库管道（含 ai_kb_document 状态机）+ SopRagAgent 单跑 + 脱敏层 SensitiveDataMasker | 知识库问答独立可用（先挂现有聊天入口） |
| Phase 2 | Supervisor 路由 + NursingAssistant 改造为 DataQueryAgent | 统一入口，双 Agent 路由 |
| Phase 3 | ReportAgent 接入 + 前端引用来源/路由轨迹 + 查询改写 + 👍👎 反馈闭环 | 完整三 Agent + 演示亮点 |
| Phase 4 | 多模态图片问答：VisionAgent（deepseek-flash）+ 聊天传图 + OSS 签名 URL 链路 | 四 Agent，支持发图提问 |

横切项：脱敏层随 Phase 1 落地（RAG 上线即有隐私数据入模风险）；Nginx SSE 配置属部署项，随首次 VM 部署执行（见 §8.1）。

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

### 12.5 视觉模型：deepseek-flash（选定） vs MiMo-VL vs 自部署开源权重

| 方案 | 优势 | 劣势 | 结论 |
| --- | --- | --- | --- |
| DeepSeek `deepseek-flash` | 与现有对话模型同供应商同协议（langchain4j-open-ai 直接复用），1M 上下文，API 免运维 | 无 | **选定** |
| 小米 MiMo-VL | 项目已有 MiMo Key（体检分析在用） | 需再维护一套模型配置与调用参数，收益不明显 | 弃 |
| DeepSeek-V4-Flash-Vision-Exp 开源自部署 | 数据不出内网 | 需 GPU，VM 资源不足，学习/演示场景不划算 | 弃 |

> 决策背景：设计初期（2026-09-24 前）DeepSeek API 无视觉模型，曾考虑 MiMo-VL；经查证官方 API 现已提供 `deepseek-flash` 多模态模型（图片走 OpenAI 兼容 content 数组 `image_url` 字段），故改选同供应商方案。

## 13. 生产化差距与刻意收窄

以下项在本设计范围内**刻意不做**（学习/演示定位），但均已想清楚生产化路径，作为已知差距记录：

| 差距项 | 当前处理 | 生产化路径 |
| --- | --- | --- |
| 知识库治理流程 | 仅 hash 幂等 + 文档登记表 | 文档增加负责人/生效日期/复审周期字段；更新文档后自动跑召回测试集回归；配套一页纸运营手册（谁上传、谁审核、多久复查） |
| Milvus 高可用 | standalone 单点（VM Docker） | 生产换 Milvus cluster（etcd+MinIO+多节点），向量数据纳入备份计划；面试要点：能讲清 standalone 与 cluster 的边界即可 |
| 语义缓存 | 无 | 高频重复问题（如"跌倒怎么处理"）做 Embedding 相似度缓存（阈值 ~0.95 命中直接返回），降 token 成本与延迟 |
| Token 成本管控 | 仅审计日志记 tokenUsage | 每用户每日限额 + 超限降级话术；成本看板按 Agent 维度聚合 token 消耗 |
| RAG 权限过滤 | 知识源纯公开制度文档，未做 | 若知识库混入老人维度数据，检索需带 Milvus metadata filter（如 dept_id 匹配 AccessScope） |
| 输出内容安全 | 正则脱敏（§6.6）+ Prompt 边界 | 增加医疗建议关键词后置拦截、接入内容安全审核 API |

> 收窄原则：单人开发 + 演示定位，优先保证"能跑通、讲得清、有闭环"，治理与 HA 类投入放在有真实用户之后。
