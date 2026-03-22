package com.openmanus.saa.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.service.mcp.McpService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class AuthorizedMcpToolCallback implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ToolDefinition TOOL_DEFINITION = ToolDefinition.builder()
            .name("callMcpTool")
            .description("Call an allowed tool from a connected MCP server using serverId, toolName, and JSON arguments.")
            .inputSchema("""
                    {
                      "type": "object",
                      "properties": {
                        "serverId": { "type": "string", "description": "Connected MCP server identifier." },
                        "toolName": { "type": "string", "description": "Exact MCP tool name." },
                        "argumentsJson": { "type": "string", "description": "JSON string of tool arguments." }
                      },
                      "required": ["serverId", "toolName", "argumentsJson"]
                    }
                    """)
            .build();

    private final McpService mcpService;
    private final McpAccessPolicy accessPolicy;

    public AuthorizedMcpToolCallback(McpService mcpService, McpAccessPolicy accessPolicy) {
        this.mcpService = mcpService;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return TOOL_DEFINITION;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toolInput);
            String serverId = root.path("serverId").asText("");
            String toolName = root.path("toolName").asText("");
            JsonNode argumentsNode = root.get("argumentsJson");
            String argumentsJson = argumentsNode == null
                    ? ""
                    : argumentsNode.isTextual() ? argumentsNode.asText() : OBJECT_MAPPER.writeValueAsString(argumentsNode);

            if (!accessPolicy.allows(serverId, toolName)) {
                return "MCP call blocked by agent policy: " + serverId + "/" + toolName;
            }

            McpToolCallResult result = mcpService.invoke(serverId, toolName, argumentsJson);
            return result.success() ? result.output() : "MCP call failed: " + result.output();
        } catch (Exception ex) {
            return "MCP call failed: " + ex.getMessage();
        }
    }
}
