package com.openmanus.saa.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
            String sessionId,
        @NotBlank String prompt
) {
}
