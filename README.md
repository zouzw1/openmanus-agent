# OpenManus Spring AI Alibaba

基于 Spring Boot 和 Spring AI Alibaba 的 OpenManus Java 实现原型。

该项目的目标不是一次性完整复刻 Python 版 OpenManus，而是先在 Java/Spring 技术栈下搭建核心运行时能力，包括：

- 基于 DashScope 的对话模型接入
- Agent 对话与工具调用
- 任务规划与计划执行
- 多 Agent 工作流执行
- MCP 服务连接与工具桥接
- Playwright 浏览器自动化
- Docker 沙箱命令执行
- 会话上下文与执行日志管理
- Workspace 文件读写工具
- 受限本地 Shell 工具
- HTTP API 对外服务

## 1. 项目定位

这是一个标准的 Spring Boot 后端服务，不是前端项目，也不是命令行工具。

应用启动后会暴露一组 REST API，调用链路如下：

1. 客户端调用 HTTP 接口
2. Controller 接收请求
3. Service 组织上下文、计划或工作流
4. `ChatClient` 调用 DashScope 模型
5. 模型可按需触发本地 `@Tool` 工具
6. 结果写入内存会话并返回

核心入口：

- 启动类：[src/main/java/com/openmanus/saa/OpenManusSpringAiAlibabaApplication.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/OpenManusSpringAiAlibabaApplication.java)
- 模型客户端配置：[src/main/java/com/openmanus/saa/config/ChatClientConfig.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/ChatClientConfig.java)

## 2. 技术栈

- Java 17
- Spring Boot 3.4.3
- Spring AI Alibaba 1.0.0.2
- DashScope Chat Model
- Spring Web / WebFlux / Validation / Actuator
- Playwright Java 1.51.0
- docker-java 3.4.1

Maven 依赖定义见：

- [pom.xml](/D:/projects/untitled/openmanus-spring-ai-alibaba/pom.xml)

## 3. 目录结构

```text
openmanus-spring-ai-alibaba
├─ src/main/java/com/openmanus/saa
│  ├─ config          配置绑定与 Bean 装配
│  ├─ controller      HTTP 接口层
│  ├─ model           请求/响应/会话/工作流模型
│  ├─ service         核心业务编排
│  │  ├─ agent        专家 Agent 执行器
│  │  ├─ browser      浏览器会话服务
│  │  ├─ mcp          MCP 连接与调用
│  │  ├─ sandbox      Docker 沙箱执行
│  │  └─ session      内存会话管理
│  └─ tool            暴露给 LLM 的工具
└─ src/main/resources
   └─ application.yml 应用配置
```

## 4. 核心模块说明

### 4.1 配置层 `config`

主要负责读取 `application.yml` 中的配置。

- [OpenManusProperties.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/OpenManusProperties.java)
  - Agent 最大步骤数
  - workspace 路径
  - shell 开关
  - 系统提示词
- [BrowserProperties.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/BrowserProperties.java)
  - 浏览器启用开关、是否 headless、超时
- [McpProperties.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/McpProperties.java)
  - MCP 是否启用、预置服务器配置
- [SandboxProperties.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/config/SandboxProperties.java)
  - Docker 镜像、内存限制、超时、网络开关

### 4.2 控制器层 `controller`

对外提供 REST API。

- [AgentController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/AgentController.java)
  - `/api/agent/chat`
  - `/api/agent/plan`
  - `/api/agent/execute`
  - `/api/agent/workflow/execute`
- [BrowserController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/BrowserController.java)
  - 浏览器动作与状态查询
- [McpController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/McpController.java)
  - MCP 服务连接、调用、断开
- [SandboxController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/SandboxController.java)
  - Docker 沙箱命令执行
- [SessionController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/SessionController.java)
  - 会话列表、会话详情、删除会话

### 4.3 服务层 `service`

负责核心业务逻辑。

- [ManusAgentService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/ManusAgentService.java)
  - 通用对话
  - 会话历史注入
  - 工具调用入口
  - 先规划后执行
- [PlanningService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/PlanningService.java)
  - 让模型生成步骤计划
- [WorkflowService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/WorkflowService.java)
  - 为不同步骤分配 Agent 并执行
- [SessionMemoryService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java)
  - 基于内存保存消息与执行日志
- [BrowserSessionService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/browser/BrowserSessionService.java)
  - 管理 Playwright 会话，执行页面操作
- [McpService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/mcp/McpService.java)
  - 管理 MCP 连接池
- [SandboxService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/sandbox/SandboxService.java)
  - 管理 Docker 容器命令执行和日志回收

### 4.4 Agent 执行器 `service/agent`

- [GeneralAgentExecutor.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/agent/GeneralAgentExecutor.java)
  - 通用执行器，负责代码、文件、命令等常规任务
- [DataAnalysisAgentExecutor.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/agent/DataAnalysisAgentExecutor.java)
  - 数据分析执行器，偏结构化分析和报告输出

### 4.5 工具层 `tool`

这些组件通过 `@Tool` 暴露给 LLM 调用。

- [WorkspaceTools.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/WorkspaceTools.java)
  - 列出工作区文件
  - 读取文本文件
  - 写入文本文件
- [ShellTools.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/ShellTools.java)
  - 受限 PowerShell 命令执行
- [PlanningTools.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/PlanningTools.java)
  - 保存计划、读取计划
- [McpToolBridge.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/McpToolBridge.java)
  - 从 LLM 侧调用 MCP 工具
- [BrowserAutomationTools.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/BrowserAutomationTools.java)
  - 触发浏览器动作
- [SandboxTools.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/tool/SandboxTools.java)
  - 在 Docker 沙箱中执行命令

## 5. 当前已实现能力

- DashScope 大模型接入
- 基础聊天 Agent
- 计划生成
- 计划分步执行
- 多 Agent 工作流执行
- 内存态会话上下文
- Workspace 文件工具
- 受限本地 Shell 工具
- Playwright 浏览器工具
- MCP 服务连接与桥接
- Docker 沙箱执行
- REST API

## 6. 当前未完善或未实现内容

- 会话持久化到 Redis 或数据库
- 完整的鉴权与权限体系
- 更强的命令安全隔离
- 动态 MCP 工具 schema 注入到模型工具描述
- 浏览器截图、标签页、多窗口、复杂表单操作
- 远程沙箱适配器
- 更完整的测试覆盖

## 7. 环境要求

建议准备以下环境：

- JDK 17+
- Maven 3.9+
- DashScope API Key

按需启用的附加依赖：

- Docker
  - 启用 `openmanus.sandbox.enabled=true` 时需要
- Playwright 浏览器依赖
  - 启用 `openmanus.browser.enabled=true` 时需要

## 8. 配置说明

配置文件位置：

- [src/main/resources/application.yml](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/resources/application.yml)

### 8.1 最小可运行配置

最少需要提供 DashScope API Key：

```yaml
spring:
  application:
    name: openmanus-spring-ai-alibaba
  ai:
    dashscope:
      api-key: YOUR_DASHSCOPE_API_KEY
```

### 8.2 当前项目完整示例配置

```yaml
spring:
  application:
    name: openmanus-spring-ai-alibaba
  ai:
    dashscope:
      api-key: YOUR_DASHSCOPE_API_KEY

server:
  port: 8080

openmanus:
  agent:
    max-steps: 8
    workspace: ./workspace
    shell-enabled: false
    shell-timeout-seconds: 15
    workflow-use-data-analysis-agent: true
    system-prompt: |
      You are OpenManus implemented with Spring AI Alibaba.
      Solve user tasks with concise reasoning and tool usage when needed.
      Prefer reading files before editing them.
      Avoid destructive shell commands.
  browser:
    enabled: false
    headless: true
    timeout-seconds: 30
  sandbox:
    enabled: false
    image: python:3.12-slim
    working-directory: /workspace
    memory-limit: 512m
    timeout-seconds: 30
    network-enabled: false
  mcp:
    enabled: false
    servers: {}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 8.3 推荐使用环境变量

如果不希望把密钥写入文件，可以改成：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

PowerShell 中设置环境变量示例：

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
```

## 9. 启动方式

### 9.1 使用 Maven 启动

```powershell
cd D:\projects\untitled\openmanus-spring-ai-alibaba
mvn spring-boot:run
```

### 9.2 打包后运行

```powershell
mvn clean package
java -jar target\openmanus-spring-ai-alibaba-0.1.0-SNAPSHOT.jar
```

默认端口：

- `8080`

健康检查：

```http
GET /actuator/health
```

## 10. 功能开关说明

### 10.1 启用本地 Shell

```yaml
openmanus:
  agent:
    shell-enabled: true
    shell-timeout-seconds: 15
```

说明：

- 执行目录为 `openmanus.agent.workspace`
- 仅做了基础黑名单限制
- 不建议直接用于高风险生产环境

### 10.2 启用浏览器自动化

```yaml
openmanus:
  browser:
    enabled: true
    headless: true
    timeout-seconds: 30
```

说明：

- 使用 Playwright Chromium
- 当前支持动作：`go_to_url`、`click`、`input`、`scroll`、`extract_text`、`current_state`

### 10.3 启用 Docker 沙箱

```yaml
openmanus:
  sandbox:
    enabled: true
    image: python:3.12-slim
    working-directory: /workspace
    memory-limit: 512m
    timeout-seconds: 30
    network-enabled: false
```

说明：

- 需要本机 Docker 可用
- 当前实现会拉取镜像、创建容器、执行命令、收集日志、移除容器
- 只做了基础命令黑名单限制

### 10.4 启用 MCP

```yaml
openmanus:
  mcp:
    enabled: true
    servers: {}
```

也可以预配置 MCP 服务：

```yaml
openmanus:
  mcp:
    enabled: true
    servers:
      demo-sse:
        type: sse
        url: http://127.0.0.1:8000/mcp
```

或使用 stdio：

```yaml
openmanus:
  mcp:
    enabled: true
    servers:
      demo-stdio:
        type: stdio
        command: python
        args:
          - server.py
```

## 11. API 说明

### 11.1 Agent 对话

请求：

```http
POST /api/agent/chat
Content-Type: application/json
```

```json
{
  "sessionId": "demo-session-1",
  "prompt": "Summarize the current workspace"
}
```

用途：

- 普通聊天
- 自动携带部分会话历史
- 允许模型调用已注册工具

### 11.2 生成计划

请求：

```http
POST /api/agent/plan
Content-Type: application/json
```

```json
{
  "sessionId": "demo-session-1",
  "prompt": "Migrate a Python agent project to Spring AI Alibaba"
}
```

用途：

- 让模型把目标拆成 3 到 6 个步骤

### 11.3 执行计划

请求：

```http
POST /api/agent/execute
Content-Type: application/json
```

```json
{
  "sessionId": "demo-session-1",
  "prompt": "Read files in workspace and generate an overview report"
}
```

用途：

- 先生成计划
- 再逐步执行
- 执行过程记录到会话日志

### 11.4 执行工作流

请求：

```http
POST /api/agent/workflow/execute
Content-Type: application/json
```

```json
{
  "sessionId": "demo-session-1",
  "prompt": "Read data files in workspace, analyze key metrics, and provide migration recommendations"
}
```

用途：

- 自动生成带 Agent 标签的步骤
- 将步骤分配给 `manus` 或 `data_analysis` 执行

### 11.5 查询会话列表

```http
GET /api/sessions
```

### 11.6 查询指定会话

```http
GET /api/sessions/demo-session-1
```

### 11.7 删除指定会话

```http
DELETE /api/sessions/demo-session-1
```

### 11.8 浏览器操作

请求：

```http
POST /api/browser/action
Content-Type: application/json
```

```json
{
  "action": "go_to_url",
  "url": "https://spring.io"
}
```

查询状态：

```http
GET /api/browser/state
```

### 11.9 沙箱命令执行

请求：

```http
POST /api/sandbox/execute
Content-Type: application/json
```

```json
{
  "command": "python --version"
}
```

### 11.10 MCP 服务管理

查询服务：

```http
GET /api/mcp/servers
```

连接 SSE 服务：

```http
POST /api/mcp/servers/connect
Content-Type: application/json
```

```json
{
  "serverId": "demo-sse",
  "type": "sse",
  "url": "http://127.0.0.1:8000/mcp"
}
```

调用工具：

```http
POST /api/mcp/tools/call
Content-Type: application/json
```

```json
{
  "serverId": "demo-sse",
  "toolName": "search_docs",
  "argumentsJson": "{\"query\":\"Spring AI Alibaba\"}"
}
```

断开服务：

```http
DELETE /api/mcp/servers/demo-sse
```

## 12. Session 机制说明

项目当前采用内存会话。

行为如下：

- 请求中传入 `sessionId` 时，使用指定会话
- 未传 `sessionId` 时，服务端自动生成一个 UUID
- 消息历史和执行日志都仅保存在内存中
- 应用重启后会话数据会丢失

相关实现：

- [SessionMemoryService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java)

## 13. 安全与限制

当前项目是原型实现，安全控制较基础，需要明确这些限制：

- 本地 Shell 仅通过关键字黑名单做限制
- Docker 沙箱也只做了基础命令模式拦截
- 没有认证、鉴权、限流
- 没有持久化和审计能力
- MCP 调用依赖外部服务自身安全性
- 浏览器能力未隔离多租户场景

不建议直接以默认配置暴露到公网。

## 14. 当前默认行为

按当前 `application.yml`，默认状态如下：

- DashScope 已要求配置 API Key
- 端口为 `8080`
- shell 默认关闭
- browser 默认关闭
- sandbox 默认关闭
- mcp 默认关闭
- workspace 路径为 `./workspace`
- 最大执行步骤数为 `8`

## 15. 常见问题

### 15.1 启动时报 `DashScope API key must be set`

说明没有配置：

- `spring.ai.dashscope.api-key`

最小修复：

```yaml
spring:
  ai:
    dashscope:
      api-key: YOUR_DASHSCOPE_API_KEY
```

### 15.2 浏览器接口无法使用

通常是以下原因之一：

- `openmanus.browser.enabled` 没有开启
- Playwright 运行依赖不完整

### 15.3 沙箱接口无法使用

通常是以下原因之一：

- `openmanus.sandbox.enabled` 没有开启
- Docker 未启动
- Docker 当前用户无权限

### 15.4 Shell 工具不执行

通常是因为：

- `openmanus.agent.shell-enabled=false`
- 命令被黑名单拦截
- 执行超时

## 16. 后续建议

建议后续优先补充：

1. Maven Wrapper，降低环境依赖门槛
2. Redis 或数据库持久化 session
3. 基于角色或 Token 的鉴权
4. 更严格的命令执行隔离
5. 更完整的集成测试
6. 更丰富的浏览器自动化能力
7. 动态 MCP 工具注册

## 17. 参考文件

如果要继续阅读源码，建议先看这些文件：

- [pom.xml](/D:/projects/untitled/openmanus-spring-ai-alibaba/pom.xml)
- [src/main/resources/application.yml](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/resources/application.yml)
- [src/main/java/com/openmanus/saa/OpenManusSpringAiAlibabaApplication.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/OpenManusSpringAiAlibabaApplication.java)
- [src/main/java/com/openmanus/saa/controller/AgentController.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/controller/AgentController.java)
- [src/main/java/com/openmanus/saa/service/ManusAgentService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/ManusAgentService.java)
- [src/main/java/com/openmanus/saa/service/PlanningService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/PlanningService.java)
- [src/main/java/com/openmanus/saa/service/WorkflowService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/WorkflowService.java)
- [src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java](/D:/projects/untitled/openmanus-spring-ai-alibaba/src/main/java/com/openmanus/saa/service/session/SessionMemoryService.java)
