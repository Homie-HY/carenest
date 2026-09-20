# carenest 智慧养老护理管理系统

基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) v3.8.9 前后端分离框架二次开发的养老机构管理系统。后端为 Maven 多模块工程，前端为 Vue3 管理端（`carenest-ui`），覆盖老人档案、入住合同、床位、护理、健康评估、报警等养老业务全链路，并在健康评估环节接入大模型对体检报告做智能分析。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 11、Spring Boot 2.5.15、Spring Security、JWT、MyBatis-Plus 3.5.2、PageHelper |
| 数据库 / 中间件 | MySQL、Druid 1.2.23 连接池、Redis、Quartz 定时调度 |
| 前端 | Vue 3、Element Plus、Vite、Pinia |
| 接口文档 / 存储 | Knife4j（Swagger 增强）、阿里云 OSS |
| AI / 文档解析 | openai-java 2.8.1（OpenAI 兼容协议，对接小米 MiMo 大模型）、Apache PDFBox |
| 部署 | Docker Compose、Jenkins Pipeline |

## 业务功能

**机构管理端**

1. 老人管理：老人档案、护理老人关联。
2. 入住管理：入住办理、入住配置、合同管理。
3. 床位管理：楼层、房间、房型、床位四级维护。
4. 护理管理：护理等级、护理计划、护理项目。
5. 健康评估：体检报告上传 + 大模型智能分析（风险等级、健康指数、异常项解读、八大系统评分、入住与护理等级建议）。
6. 报警与设备：报警规则、报警数据、IoT 设备管理。

**用户端（Member / 家属）**

7. 家庭成员维护、房型浏览、入住预约。

**系统管理（RuoYi 内置）**

8. 用户、角色、菜单、部门、字典、参数、通知、操作/登录日志、在线用户、定时任务、代码生成、服务/缓存监控。

## 项目结构

| 模块 | 职责 |
| --- | --- |
| carenest-admin | 启动模块，系统管理与监控控制器（启动类 `com.carenest.RuoYiApplication`） |
| carenest-framework | 框架核心：安全认证、配置、拦截器、AOP |
| carenest-system | 系统领域：用户、角色、菜单、字典等 |
| carenest-nursing-platform | 护理业务模块：老人、入住、合同、床位、护理、评估、报警 |
| carenest-quartz | 定时任务调度 |
| carenest-generator | 前后端代码生成器 |
| carenest-common | 通用工具、注解、常量、异常处理，大模型调用封装（`com.carenest.common.ai`） |
| carenest-oss | 阿里云 OSS 文件存储封装 |
| carenest-ui | Vue3 + Element Plus 前端管理端 |
| sql | 数据库初始化脚本 |

## AI 能力：体检报告智能分析

目前全系统唯一的大模型落地点在**入住健康评估**业务，其余模块均为常规 CRUD。

**调用链路**

1. 前端上传体检报告 PDF → `HealthAssessmentController#uploadFile`：校验类型与大小（≤10MB）→ 存阿里云 OSS → `PDFUtil.pdfToString` 抽取纯文本 → 以身份证号为 field 暂存 Redis Hash `healthReport`。
2. 提交评估 → `HealthAssessmentServiceImpl#insertHealthAssessment`：从 Redis 取报告文本，按「专业医生视角」拼装 Prompt（约定输出总检日期、风险等级、健康指数、风险占比分布、异常项七字段、八大系统评分、报告总结，并强制返回纯 JSON）。
3. `MiMiModelInvoker#miMoInvoker` 通过 OpenAI 兼容 SDK 发起 ChatCompletion 调用。
4. `cleanAiResponse` 剥离 Markdown 代码块与前后缀杂文，截取首个 `{` 到末个 `}`，反序列化为 `HealthReportVo`；解析失败抛业务异常提示重新提交。
5. `saveHealthAssessment` 落库：由身份证号推导出生日期/年龄/性别，按健康分推导护理等级（90+ 四级 … <60 特级）与入住建议（≥60 建议入住），疾病风险分布、异常分析、八大系统评分以 JSON 字符串入库。

**相关代码**

| 位置 | 作用 |
| --- | --- |
| `carenest-common/.../common/ai/LLMConfig.java` | 绑定 `llm.xiaomi` 配置（apiKey / baseUrl / model） |
| `carenest-common/.../common/ai/MiMiModelInvoker.java` | 大模型调用封装 |
| `carenest-nursing-platform/.../service/impl/HealthAssessmentServiceImpl.java` | Prompt 设计、结果清洗、JSON 解析与业务落库 |
| `carenest-nursing-platform/.../controller/HealthAssessmentController.java` | 报告上传、PDF 解析、Redis 暂存 |

**配置**

`carenest-admin/src/main/resources/application-{dev,test,prod}.yml` 中，API Key 以环境变量占位，仓库内不存放真实密钥：

```yaml
llm:
  xiaomi:
    api-key: ${LLM_XIAOMI_API_KEY:}
    base-url: https://api.xiaomimimo.com/v1
    model: mimo-v2.5-pro
```

> 注意：MiMo 不支持 `response_format`，JSON 结构完全依赖 Prompt 约束 + 服务端 `cleanAiResponse` 兜底清洗。未设置 `LLM_XIAOMI_API_KEY` 时应用仍能正常启动，但提交健康评估会调用失败。

## 环境变量与密钥管理

所有敏感凭证统一通过环境变量注入，**不得硬编码到 yml / Dockerfile / Java 代码**。根目录提供 `.env.example` 模板，复制为 `.env`（已 gitignore）后填入真实值：

```powershell
Copy-Item .env.example .env
```

| 变量 | 用途 | 消费方 |
| --- | --- | --- |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS 文件上传 | `AliyunOSSOperator`（`EnvironmentVariableCredentialsProvider`） |
| `LLM_XIAOMI_API_KEY` | 小米 MiMo 大模型 | `llm.xiaomi.api-key` |
| `WECHAT_APP_SECRET` | 微信小程序家属端登录 | `wechat.appSecret` |
| `QIANFAN_API_KEY` | 百度千帆（仅 `carenest-common` 单元测试） | `QianfanAIModelTest1` |

注入方式：

- **docker compose**：自动读取仓库根目录 `.env`。
- **IDEA 本地运行**：Run Configuration → Environment variables 填入上述变量（或直接 `setx` / `$env:` 设系统变量）。
- **Jenkins 部署**：用 Credentials 绑定为环境变量，`carenest-admin/deploy.sh` 会在 `docker run` 时透传给容器；`Dockerfile` 内不保留任何密钥。

> `wechat.appId`、OSS `endpoint`/`bucketName` 属公开信息，仍保留在 yml 中。

## 快速开始

**后端**

1. 创建 MySQL 数据库，依次导入 `sql/ry_20250417.sql`、`sql/quartz.sql`、`sql/carenest-dev06-init.sql`。
2. 修改 `carenest-admin/src/main/resources/application-druid.yml` 中的数据库连接，并确保本地 Redis 已启动。
3. 复制 `.env.example` 为 `.env` 并填入 OSS、MiMo、微信等密钥，再按上节说明注入到运行环境。
4. 运行启动类 `com.carenest.RuoYiApplication`，默认端口见 `application.yml`（激活 profile 为 `dev`）。

**前端**

```bash
cd carenest-ui
npm install
npm run dev
```

## 部署

- `docker-compose.yml`：容器化一键编排，敏感变量从根目录 `.env` 读取（OSS 凭证未配置会直接报错中断）；`carenest-admin` 目录内含 `Dockerfile` 与 `deploy.sh`。
- `Jenkinsfile`：拉取代码 → Maven 打包 → Docker 构建镜像 → 执行部署脚本；密钥需在 Jenkins Credentials 中维护。
- 镜像本身不包含任何凭证，仅靠运行时 `-e` 注入；更换密钥无需重新构建镜像。

## 分支与提交规范

- 远程仓库仅保留 `master` 单一分支，所有开发与推送均在 master 上进行。
- 提交信息统一格式：`存档N：关键词`（如 `存档2：前端代码初始化文件`），N 为递增存档序号。

## 致谢

- [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)：前后端分离的 Java 快速开发框架（v3.8.9）。
