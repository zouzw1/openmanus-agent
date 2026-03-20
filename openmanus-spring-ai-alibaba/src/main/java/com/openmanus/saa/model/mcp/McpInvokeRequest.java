package com.openmanus.saa.model.mcp;

import jakarta.validation.constraints.NotBlank;

public record McpInvokeRequest(
        @NotBlank String serverId,
        @NotBlank String toolName,
        String argumentsJson
) {
}
