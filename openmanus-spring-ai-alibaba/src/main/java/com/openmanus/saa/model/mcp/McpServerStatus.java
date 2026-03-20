package com.openmanus.saa.model.mcp;

import java.util.List;

public record McpServerStatus(
        String serverId,
        String type,
        boolean connected,
        String endpoint,
        List<String> tools
) {
}
