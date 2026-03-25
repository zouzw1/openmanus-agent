# study-plan-demo

一个独立的 Spring Boot 示例项目，用来演示如何以 SDK 的方式接入 `openmanus-spring-ai-alibaba`，并在保留 SDK 基础能力的前提下，追加自己的业务能力。

## 这个项目演示什么

`study-plan-demo` 主要演示三件事：
- 在 SDK 基础 agent 之外，追加自己的业务 agent `study-planner`
- 在业务项目中声明自己的本地 `@Tool`，并由 SDK 自动发现
- 通过 `IntentRecognizer` 为学习计划类请求提供定制路由

同时，这个示例还启用了 MCP，并连接仓库内的 `mcp-test-service`。

## 与 SDK 的关系

该项目不是复制 SDK 代码，而是普通业务项目：
- 依赖 `openmanus-spring-ai-alibaba`
- 复用 SDK 提供的 controller、workflow、session、tool registry、MCP 集成等能力
- 在此基础上追加自己的 agent、tool、intent

因此计划阶段看到的是聚合后的能力集合：
- SDK 内置 agent：`manus`
- SDK 内置 agent：`data_analysis`
- 业务自定义 agent：`study-planner`

## 当前自定义内容

### 1. 自定义 agent

文件：
- [StudyPlanAgentConfiguration.java](./src/main/java/com/openmanus/demo/studyplan/config/StudyPlanAgentConfiguration.java)

该配置会额外注入一个 `study-planner` agent，用于处理学习计划类任务。

### 2. 自定义本地工具

文件：
- [LearningPlanTools.java](./src/main/java/com/openmanus/demo/studyplan/tool/LearningPlanTools.java)

当前提供的工具：
- `recommendLearningGoals`
- `buildWeeklyStudySchedule`
- `generatePracticeChecklist`

### 3. 自定义意图识别器

文件：
- [StudyPlanIntentRecognizer.java](./src/main/java/com/openmanus/demo/studyplan/intent/StudyPlanIntentRecognizer.java)

它会优先识别以下场景：
- 学习计划
- 备考计划
- 学习路线 / roadmap
- 学习计划导出请求

命中后会给出：
- `preferredAgentId = study-planner`
- `routeMode = DIRECT_CHAT` 或 `PLAN_EXECUTE`
- 一组学习计划领域的 `planningHints`

## 配置说明

配置文件位于：
- [application.yml](./src/main/resources/application.yml)

当前只保留少量必要配置：
- DashScope API Key
- 服务端口 `8091`
- 业务系统提示词
- MCP 启用与 `demo-sse` 地址
- `skills.enabled = false`

这体现了“约定大于配置”的接入方式：
- 大部分基础配置沿用 SDK 默认值
- 只有业务差异项才在示例项目中显式覆盖

## 运行前准备

### 1. 设置 API Key

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
```

### 2. 启动 MCP 测试服务

```powershell
mvn -pl mcp-test-service spring-boot:run
```

### 3. 启动示例项目

```powershell
mvn -pl study-plan-demo spring-boot:run
```

默认地址：`http://localhost:8091`

## 示例请求

### 学习计划聊天

```powershell
curl -X POST http://localhost:8091/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{"sessionId":"study-chat-001","prompt":"帮我做一个 12 周的 Spring Boot 学习计划，每周 10 小时，适合初学者"}'
```

### 学习计划执行

```powershell
curl -X POST http://localhost:8091/api/agent/execute `
  -H "Content-Type: application/json" `
  -d '{"sessionId":"study-exec-001","prompt":"为初学者生成一个 8 周 Java 学习计划，并保存到 workspace"}'
```

### MCP 联动示例

```powershell
curl -X POST http://localhost:8091/api/agent/chat `
  -H "Content-Type: application/json" `
  -d '{"sessionId":"study-mcp-001","prompt":"帮我做一个 12 周 Spring Boot 学习计划，并结合当前时间给出每周节奏建议"}'
```

## 适合用来验证的能力点

这个示例适合验证：
- SDK 内置 agent 与业务自定义 agent 是否可同时参与 planning
- 新项目自己的 `@Tool` 是否能被 SDK 自动发现
- 业务意图识别是否能优先路由到自定义 agent
- 在业务项目中启用 MCP 后，是否能正常连接并使用 `demo-sse`

## 说明

- 如果业务项目不启用 MCP，即使 SDK 支持 MCP，也不会自动连接任何 MCP 服务
- 如果未显式配置默认聊天 agent，SDK 默认仍会回落到 `manus`
- 对学习计划类请求，`StudyPlanIntentRecognizer` 会优先推荐 `study-planner`
