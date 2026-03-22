package com.openmanus.saa.service.agent;

import java.util.List;

public record AgentExecutionResult(
        String content,
        List<String> usedTools,
        List<String> usedToolCalls
) {
    public AgentExecutionResult {
        usedTools = usedTools == null ? List.of() : List.copyOf(usedTools);
        usedToolCalls = usedToolCalls == null ? List.of() : List.copyOf(usedToolCalls);
    }
}
