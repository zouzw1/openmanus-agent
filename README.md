# OpenManus Spring AI Alibaba

基于 Spring Boot、Spring AI Alibaba 和 DashScope 的 Agent 后端示例项目。

当前仓库是一个多模块 Maven 工程，包含：

- `openmanus-spring-ai-alibaba`
  主服务。负责统一 `/chat` 入口、计划生成、计划执行、human-in-the-loop 恢复、MCP 工具调用、skills 接入。
- `mcp-test-service`
  演示用 MCP Server。提供天气、时间、回声、求和、旅行建议等示例工具。

项目定位是一个可扩展的 Agent 服务端，而不是某个固定领域的单一应用。

## 项目结构

```text
.
├─ pom.xml
├─ README.md
├─ skills
│  ├─ project
│  └─ user
├─ openmanus-spring-ai-alibaba
│  ├─ pom.xml
│  └─ src/main
│     ├─ java/com/openmanus/saa
│     │  ├─ config
│     │  ├─ controller
│     │  ├─ model
│     │  ├─ service
│     │  │  ├─ agent
│     │  │  ├─ browser
│     │  │  ├─ mcp
│     │  │  ├─ sandbox
│     │  │  └─ session
│     │  ├─ tool
│     │  └─ util
│     └─ resources
│        └─ application.yml
└─ mcp-test-service
   ├─ pom.xml
   └─ src/main
      ├─ java/com/openmanus/mcptest
      └─ resources
         └─ application.yml
```

## 主要能力

- 统一 `POST /api/agent/chat` 入口
- LLM 路由三种模式
  - `chat`
  - `plan`
  - `plan-execute`
- `plan-execute` 使用统一的工作流状态机执行
- human-in-the-loop 暂停与恢复
- 自然语言反馈意图识别
- MCP Server 自动连接与工具桥接
- Skills 自动发现、懒加载和 `read_skill` 调用
- 会话记忆与执行日志
- 可选 Browser / Shell / Sandbox 工具能力
- 输出语言跟随用户输入语言

## 技术栈

- Java 17
- Maven
- Spring Boot 3.4.3
- Spring AI Alibaba 1.1.2.2
- DashScope
- Spring AI MCP
- Playwright Java
- docker-java

## 运行要求

- JDK 17+
- Maven 3.9+
- DashScope API Key

建议不要把真实 API Key 写入仓库。优先使用环境变量：

```powershell
$env:SPRING_AI_DASHSCOPE_API_KEY="your-api-key"
```

## 配置说明

主服务默认配置文件：

- [application.yml](/D:/projects/untitled/openmanus-spring-ai-alibaba/openmanus-spring-ai-alibaba/src/main/resources/application.yml)

当前关键配置：

```yaml
server:
  port: 8080

openmanus:
  agent:
    max-steps: 8
    workspace: ./workspace
    shell-enabled: false
    workflow-use-data-analysis-agent: true
  browser:
    enabled: false
  sandbox:
    enabled: false
  mcp:
    enabled: true
    servers:
      demo-sse:
        type: sse
        url: http://127.0.0.1:18080/mcp
        sse-endpoint: /sse
  skills:
    enabled: true
    lazy-load: true
    project-skills-directory: ./skills/project
    user-skills-directory: ./skills/user
```

演示 MCP 服务默认配置：

- [application.yml](/D:/projects/untitled/openmanus-spring-ai-alibaba/mcp-test-service/src/main/resources/application.yml)

当前默认端口：

- 主服务：`8080`
- MCP 测试服务：`18080`

## 快速启动

建议先启动 MCP 服务，再启动主服务。

### 1. 启动 MCP 测试服务

```powershell
mvn -pl mcp-test-service spring-boot:run
```

### 2. 启动主服务

```powershell
mvn -pl openmanus-spring-ai-alibaba spring-boot:run
```

### 3. 验证 MCP 是否已连接

```http
GET /api/mcp/servers
```

如果返回中包含 `demo-sse`，说明主服务已经连上演示 MCP 服务。

## API 概览

### 1. 统一入口 `/api/agent/chat`

```http
POST /api/agent/chat
Content-Type: application/json
```

```json
{
  "sessionId": "demo-chat-001",
  "prompt": "你好"
}
```

```json
{
  "sessionId": "demo-chat-002",
  "prompt": "给我规划一个下周学习计划"
}
```

```json
{
  "sessionId": "demo-chat-003",
  "prompt": "帮我分析当前项目结构并总结模块职责"
}
```

`/api/agent/chat` 会先经过路由节点，再自动进入以下模式之一：

- `chat`
  适合问候语、闲聊、简单说明、轻量问答
- `plan`
  适合只要计划、清单、路线、方案，不要求立刻执行
- `plan-execute`
  适合需要多步执行、工具调用、分析、生成、修改、验证的任务

### 2. 显式只生成计划 `/api/agent/plan`

```http
POST /api/agent/plan
Content-Type: application/json
```

```json
{
  "sessionId": "demo-plan-001",
  "prompt": "分析当前项目结构，并给出阅读顺序"
}
```

### 3. 显式执行计划 `/api/agent/execute`

```http
POST /api/agent/execute
Content-Type: application/json
```

```json
{
  "sessionId": "demo-exec-001",
  "prompt": "读取项目结构并总结模块职责"
}
```

说明：

- `/execute` 现在已经复用统一的 `plan-execute` 状态机
- 旧的 `/workflow/execute` 已不再作为主入口

### 4. 查询待处理反馈

```http
GET /api/agent/pending-feedback/{sessionId}
```

兼容旧路径：

```http
GET /api/agent/workflow/pending-feedback/{sessionId}
```

### 5. 提交反馈并恢复执行

推荐直接提交自然语言：

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

也兼容显式动作：

```json
{
  "sessionId": "wf-demo-001",
  "action": "SKIP_STEP"
}
```

兼容旧路径：

```http
POST /api/agent/workflow/feedback
```

支持动作：

- `PROVIDE_INFO`
- `MODIFY_AND_RETRY`
- `RETRY`
- `SKIP_STEP`
- `ABORT_PLAN`

### 6. 会话管理

```http
GET /api/sessions
GET /api/sessions/{sessionId}
DELETE /api/sessions/{sessionId}
```

### 7. MCP 管理

```http
GET /api/mcp/servers
POST /api/mcp/servers/connect
POST /api/mcp/tools/call
DELETE /api/mcp/servers/{serverId}
```

### 8. Skills 管理

```http
GET /api/skills
GET /api/skills/{skillName}
GET /api/skills/{skillName}/content
GET /api/skills/instructions/load
POST /api/skills/reload
```

### 9. 可选 Browser / Sandbox 接口

这些能力默认关闭，只在对应配置启用后建议使用：

```http
POST /api/browser/action
GET /api/browser/state
POST /api/sandbox/execute
```

## 统一响应格式

`/api/agent/chat` 和 `/api/agent/execute` 都返回统一的 `AgentResponse` 结构：

```json
{
  "mode": "plan-execute",
  "objective": "读取项目结构并总结模块职责",
  "summary": "面向用户的结果总结",
  "content": "Markdown 详细结果",
  "steps": [
    "步骤 1",
    "步骤 2"
  ],
  "workflowSteps": [
    {
      "agent": "manus",
      "description": "步骤描述",
      "status": "COMPLETED"
    }
  ],
  "executionLog": [
    "步骤执行日志"
  ],
  "workflowSummary": {
    "status": "COMPLETED",
    "statusLabel": "完全执行成功",
    "totalSteps": 2,
    "completedSteps": 2,
    "skippedSteps": 0,
    "failedSteps": 0,
    "requiresHumanFeedback": false,
    "currentStep": null,
    "userMessage": "面向用户的执行总结"
  },
  "pendingFeedback": null
}
```

### `summary` 的语义

- `chat`
  `summary` 就是最终给用户的答复
- `plan`
  `summary` 就是最后的计划内容
- `plan-execute`
  `summary` 是对执行结果的总结，重点说明：
  - 执行是否完成
  - 当前是否受阻
  - 关键结果
  - 还需要用户提供什么帮助

### `content` 的语义

- `chat`
  `content` 是 Markdown 展示版回复
- `plan`
  `content` 是 Markdown 计划内容
- `plan-execute`
  `content` 是详细执行结果，包含步骤状态、日志和待反馈信息

## Human-in-the-loop 行为

`plan-execute` 现在使用统一的工作流状态机，支持暂停和恢复。

执行流程大致为：

1. 生成结构化计划
2. 按步骤执行
3. 记录 `workflowSteps` 和 `executionLog`
4. 如果遇到需要澄清的问题，则返回 `pendingFeedback`
5. 用户通过 `/api/agent/feedback` 提交自然语言反馈
6. 服务继续从中断步骤或后续步骤执行

### `pendingFeedback` 返回内容

当执行暂停时，响应中会带：

- `sessionId`
- `objective`
- `planId`
- `stepIndex`
- `failedStep`
- `steps`
- `errorMessage`
- `suggestedAction`
- `userMessage`

### 自然语言反馈

当前前端不需要强制传 `action`。可以直接传：

```json
{
  "sessionId": "wf-demo-001",
  "userInput": "跳过这一步"
}
```

```json
{
  "sessionId": "wf-demo-001",
  "userInput": "结束流程"
}
```

```json
{
  "sessionId": "wf-demo-001",
  "userInput": "补充一下参数后重试"
}
```

服务端会综合：

- 规则识别
- LLM 意图识别

得到最终反馈动作。

## Skills 使用方式

当前项目已经把 skills 接到了全局 `ChatClient`。

### 技术实现

- `SkillRegistry` 从文件系统发现 skills
- `SpringAiSkillAdvisor` 挂到全局 `ChatClient`
- `read_skill` 作为 ToolCallback 注册
- skill 内容按需懒加载，不是每次把所有 skill 全文注入 prompt

### 目录约定

- 项目级 skills：`./skills/project`
- 用户级 skills：`./skills/user`

每个 skill 必须是一个目录，目录内包含 `SKILL.md`，例如：

```text
skills/project/project-planning/SKILL.md
```

### `SKILL.md` 最小格式

```md
---
name: project-planning
description: Help build user-friendly plans and ask for missing inputs in plain language.
---

# Project Planning Skill

Use this skill when the user asks for a plan, checklist, or roadmap.
```

### 重新加载 skills

新增或修改 skill 后，可以：

- 重启服务
- 或调用 `POST /api/skills/reload`

## 输出语言

系统会尽量跟随用户输入语言输出：

- 中文输入，优先返回中文
- 英文输入，优先返回英文

该规则适用于：

- 普通对话
- 计划生成
- 计划执行
- human-in-the-loop 提示

## MCP 测试服务

`mcp-test-service` 当前示例工具包括：

- `health_check`
- `echo_text`
- `sum_numbers`
- `current_time`
- `get_weather`
- `get_forecast`
- `travel_guide`

默认以 SSE 方式暴露，基路径和端点由 [application.yml](/D:/projects/untitled/openmanus-spring-ai-alibaba/mcp-test-service/src/main/resources/application.yml) 配置：

- `web-base-path: /mcp`
- `sse-endpoint: /sse`

因此主服务默认连接：

- `http://127.0.0.1:18080/mcp/sse`

## 推荐调试顺序

1. 启动 `mcp-test-service`
2. 启动 `openmanus-spring-ai-alibaba`
3. 调用 `GET /api/mcp/servers`
4. 优先调用 `POST /api/agent/chat`
5. 如需只看计划，再调 `POST /api/agent/plan`
6. 如需显式执行，再调 `POST /api/agent/execute`
7. 如遇暂停，调：
   - `GET /api/agent/pending-feedback/{sessionId}`
   - `POST /api/agent/feedback`
8. 如需检查 skills，再调：
   - `GET /api/skills`
   - `POST /api/skills/reload`

## 开发命令

### 构建整个项目

```powershell
mvn clean package -DskipTests
```

### 编译主模块

```powershell
mvn -pl openmanus-spring-ai-alibaba -DskipTests compile
```

### 运行测试

```powershell
mvn -pl openmanus-spring-ai-alibaba test
mvn -pl mcp-test-service test
```

## 当前约束

- Session、执行日志和待反馈状态当前保存在内存中，服务重启后会丢失
- 计划生成、工具推断和参数抽取仍然是 LLM 驱动，虽然做了过滤和校验，但不能保证复杂场景下绝对正确
- Browser / Shell / Sandbox 默认关闭，需要显式启用并评估安全风险
- 当前不建议直接公网暴露，生产化还需要补齐认证、授权、限流、隔离和审计

## 后续演进方向

- 引入持久化 Session / 执行状态
- 强化 `plan-execute` 状态机和补偿机制
- 按 agent 或场景做更精细的工具和 skills 暴露
- 加强 Browser / Shell / Sandbox 隔离
- 进一步收敛前端只依赖 `/chat` 和 `/feedback`
