package com.openmanus.saa.model.mcp;

public record McpToolCallResult(
        String serverId,
        String toolName,
        boolean success,
        String output
) {
}
