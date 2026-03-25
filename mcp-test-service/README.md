# mcp-test-service

一个用于联调和演示的 Spring AI MCP Server。

它提供一组简单但足够实用的 MCP tools，方便主服务模块和示例项目验证：
- MCP 连接是否成功
- `callMcpTool` 是否可用
- 计划阶段是否能识别和使用外部工具

## 模块职责

该服务主要用于本仓库内联调，不承担复杂业务逻辑。

当前提供的工具包括：
- `health_check`
- `echo_text`
- `sum_numbers`
- `current_time`
- `get_weather`
- `get_forecast`
- `travel_guide`

## 运行方式

从仓库根目录执行：

```powershell
mvn -pl mcp-test-service spring-boot:run
```

默认端口：`18080`

## MCP 暴露方式

配置文件位于：
- [application.yml](./src/main/resources/application.yml)

当前使用 SSE 协议，关键配置如下：
- `web-base-path: /mcp`
- `sse-endpoint: /sse`
- `sse-message-endpoint: /mcp/message`

因此主服务连接时使用的基地址通常是：
- `http://127.0.0.1:18080/mcp`

SSE 端点为：
- `http://127.0.0.1:18080/mcp/sse`

## 与主服务对接

在接入方配置中启用 MCP，例如：

```yaml
openmanus:
  mcp:
    enabled: true
    servers:
      demo-sse:
        type: sse
        url: http://127.0.0.1:18080/mcp
        sse-endpoint: /sse
```

## 快速验证

### 1. 启动本服务

```powershell
mvn -pl mcp-test-service spring-boot:run
```

### 2. 启动主服务或示例项目

例如：

```powershell
mvn -pl openmanus-spring-ai-alibaba spring-boot:run
```

或：

```powershell
mvn -pl study-plan-demo spring-boot:run
```

### 3. 检查是否已连接

```http
GET /api/mcp/servers
```

如果返回中包含 `demo-sse`，说明连接已建立。

### 4. 触发一次 MCP 使用场景

例如请求天气、时间、旅行建议等，让 Agent 在执行时调用 MCP 工具。

## 适用场景

- 验证 SDK 的 MCP 集成链路
- 验证 Agent 的 MCP 权限控制
- 验证 planner 是否能识别外部 MCP 能力
- 本地演示多模块联调
