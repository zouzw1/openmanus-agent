package com.openmanus.saa.model;

import com.openmanus.saa.model.AgentTask;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AgentRequest(
        String sessionId,
        String agentId,
        @NotBlank String prompt,
        ExecutionMode mode,
        boolean forceMultiAgent,
        boolean forceSingleAgent,
        List<AgentTask> tasks
) {
    public AgentRequest {
        if (mode == null) {
            mode = ExecutionMode.AUTO;
        }
        if (tasks == null) {
            tasks = List.of();
        }
    }

    /** 便捷构造：只提供 prompt */
    public AgentRequest(String prompt) {
        this(null, null, prompt, ExecutionMode.AUTO, false, false, List.of());
    }

    public boolean isForceMultiAgent() {
        return forceMultiAgent;
    }

    public boolean isForceSingleAgent() {
        return forceSingleAgent;
    }

    public enum ExecutionMode {
        AUTO,
        SINGLE_AGENT,
        MULTI_AGENT,
        EXPERT
    }
}
