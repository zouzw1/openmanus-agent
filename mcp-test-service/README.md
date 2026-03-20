# MCP Test Service

这是一个用于联调主项目 MCP 接入能力的标准 Spring AI MCP Server。

实现方式：

- Spring Boot
- Spring WebFlux
- `spring-ai-starter-mcp-server-webflux`
- Spring AI `@Tool` + `MethodToolCallbackProvider`

默认端口：`18080`

默认 SSE 端点：

- `GET /sse`

默认消息回调端点：

- `POST /mcp/message`

## 启动

```powershell
cd mcp-test-service
mvn spring-boot:run
```

## 可用 MCP 工具

- `health_check`
- `echo_text`
- `sum_numbers`
- `current_time`

## 主项目连接配置

把主项目的 MCP 地址改成：

```yaml
openmanus:
  mcp:
    enabled: true
    servers:
      demo-sse:
        type: sse
        url: http://127.0.0.1:18080
        sse-endpoint: /sse
```

## 快速验证思路

1. 启动本服务
2. 启动主项目
3. 调用主项目 `GET /api/mcp/servers`，确认 `demo-sse` 已连接且工具列表已拉取
4. 再调用主项目的 `/api/agent/chat` 或 `/api/mcp/tools/call`

手工验证时，建议直接用支持 SSE 的标准 MCP client，而不是自己拼旧版 JSON-RPC POST 请求。
