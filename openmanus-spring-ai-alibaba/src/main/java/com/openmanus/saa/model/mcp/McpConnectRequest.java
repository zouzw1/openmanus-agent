package com.openmanus.saa.model.mcp;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record McpConnectRequest(
        @NotBlank String serverId,
        @NotBlank String type,
        String url,
        String sseEndpoint,
        String command,
        List<String> args
) {
}
