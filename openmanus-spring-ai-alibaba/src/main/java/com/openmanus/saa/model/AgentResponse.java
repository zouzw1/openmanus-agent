package com.openmanus.saa.model;

import java.util.List;

public record AgentResponse(
        String mode,
        String content,
        List<String> steps
) {
}
