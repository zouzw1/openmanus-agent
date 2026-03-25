# OpenManus Spring AI Alibaba

一个多模块 Maven 工程，包含：
- 一个可独立运行的 OpenManus 后端服务模块
- 一个用于联调的 MCP 测试服务模块
- 一个以 SDK 方式接入的业务示例模块

仓库适合两类使用方式：
- 直接运行 `openmanus-spring-ai-alibaba`，作为通用 Agent 服务
- 在自己的 Spring Boot 项目中引入 `openmanus-spring-ai-alibaba`，复用基础能力，并追加自定义 agent、tool、intent 识别逻辑

## 模块说明

- `openmanus-spring-ai-alibaba`
  SDK 与主服务模块。提供工作流执行、Agent 注册、MCP 接入、Skills、会话管理、意图识别扩展点等核心能力。
- `mcp-test-service`
  一个标准 Spring AI MCP Server 示例，提供天气、时间、旅行建议等工具，供主服务或示例项目联调。
- `study-plan-demo`
  一个基于 SDK 的独立 Spring Boot 示例。它复用 SDK 内置的基础 agent，并追加自己的 `study-planner` agent、本地学习计划工具和业务意图识别器。

## 项目结构

```text
.
|-- pom.xml
|-- README.md
|-- agents
|-- skills
|-- openmanus-spring-ai-alibaba
|   |-- pom.xml
|   `-- src
|-- mcp-test-service
|   |-- pom.xml
|   `-- src
`-- study-plan-demo
    |-- pom.xml
    `-- src
```

## 仓库角色划分

### 1. parent 工程
根目录 `pom.xml` 是聚合工程，负责统一版本和模块编排：
- `openmanus-spring-ai-alibaba`
- `mcp-test-service`
- `study-plan-demo`

### 2. SDK / 主服务
`openmanus-spring-ai-alibaba` 同时承担两种角色：
- 作为独立服务运行
- 作为业务项目依赖引入

SDK 当前内置提供基础 agent：
- `manus`
- `data_analysis`

业务项目接入后可以继续追加自己的 agent 和本地工具，而不是替换 SDK 基础能力。

### 3. 示例服务
- `mcp-test-service` 演示如何提供 MCP 工具
- `study-plan-demo` 演示如何消费 SDK 并扩展业务能力

## 当前核心能力

- 统一聊天与任务入口
- `chat / plan / plan-execute` 路由
- 工作流计划生成、执行、暂停与恢复
- 人工干预反馈
- MCP Server 连接与桥接调用
- Skills 装载与 `read_skill` 调用
- agent 聚合注册
- 本地 `@Tool` 自动发现
- 可扩展意图识别层
- 产物路径追踪与绝对路径返回

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- DashScope API Key

建议通过环境变量提供 API Key：

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
```

### 1. 启动 MCP 测试服务

```powershell
mvn -pl mcp-test-service spring-boot:run
```

默认地址：`http://localhost:18080`

### 2. 启动主服务

```powershell
mvn -pl openmanus-spring-ai-alibaba spring-boot:run
```

默认地址：`http://localhost:8080`

### 3. 启动 SDK 接入示例

```powershell
mvn -pl study-plan-demo spring-boot:run
```

默认地址：`http://localhost:8091`

## 常用接口

### 通用聊天入口

```http
POST /api/agent/chat
Content-Type: application/json
```

```json
{
  "sessionId": "demo-chat-001",
  "prompt": "帮我做一个 12 周的 Spring Boot 学习计划"
}
```

### 显式计划执行

```http
POST /api/agent/execute
Content-Type: application/json
```

```json
{
  "sessionId": "demo-exec-001",
  "prompt": "为标准成年人生成一份健身入门建议 Word 文档"
}
```

### 查询待处理反馈

```http
GET /api/agent/pending-feedback/{sessionId}
```

### 提交反馈并续跑

```http
POST /api/agent/feedback
Content-Type: application/json
```

```json
{
  "sessionId": "wf-demo-001",
  "userInput": "用修正后的参数再试"
}
```

## 约定大于配置

当前项目已经尽量使用默认值。业务项目通常只需要配置：
- 模型 API Key
- 端口
- 是否启用 MCP，以及 MCP server 地址
- 必要的业务差异项

例如 `study-plan-demo` 就只保留了少量必要配置，而把 `planning-validation`、`sandbox`、`browser`、`workspace` 等项交给 SDK 默认值处理。

## 扩展方式

### 扩展 agent
业务项目可通过自定义 `AgentConfigSource` 追加自己的 agent。SDK 内置 agent 会继续保留。

### 扩展本地工具
业务项目只需声明 Spring Bean，并在方法上使用 `@Tool`，SDK 会自动发现。

### 扩展意图识别
业务项目可实现 `IntentRecognizer`，为特定领域请求提供：
- `routeMode`
- `preferredAgentId`
- `planningHints`
- `attributes`

## 模块文档

- [SDK / 主服务模块](./openmanus-spring-ai-alibaba/README.md)
- [MCP 测试服务](./mcp-test-service/README.md)
- [study-plan-demo](./study-plan-demo/README.md)

## 构建命令

### 编译整个仓库

```powershell
mvn clean package -DskipTests
```

### 只编译 SDK 模块

```powershell
mvn -pl openmanus-spring-ai-alibaba -DskipTests compile
```

### 只编译示例项目并带上依赖模块

```powershell
mvn -pl study-plan-demo -am -DskipTests compile
```

## 说明

- Session 与执行状态当前主要保存在内存中，适合开发和演示环境
- Browser、Sandbox、Shell 等能力默认偏保守，需要时再显式开启
- MCP 是否可用，取决于运行时配置，而不是 SDK 是否包含相关代码
- 生产化前仍建议补充认证、鉴权、限流、审计与持久化能力
