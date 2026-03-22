package com.openmanus.saa.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
            String sessionId,
            String agentId,
        @NotBlank String prompt
) {
}
