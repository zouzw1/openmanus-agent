package com.openmanus.saa.model;

import java.util.Set;

public record AgentDefinitionResponse(
        String id,
        String name,
        boolean enabled,
        String executorType,
        String description,
        boolean planningVisible,
        int priority,
        String promptFile,
        Set<String> localTools,
        Set<String> mcpServers,
        Set<String> mcpTools,
        Set<String> skills
) {
}
