package com.openmanus.saa.model;

import java.util.List;

public record AgentCapabilitySnapshot(
        String agentId,
        String executorType,
        String description,
        List<String> localTools,
        List<String> mcpTools,
        List<String> skills
) {
    public AgentCapabilitySnapshot {
        agentId = agentId == null ? "" : agentId.trim();
        executorType = executorType == null ? "" : executorType.trim();
        description = description == null ? "" : description.trim();
        localTools = localTools == null ? List.of() : List.copyOf(localTools);
        mcpTools = mcpTools == null ? List.of() : List.copyOf(mcpTools);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public boolean supportsReadSkill() {
        return !skills.isEmpty();
    }

    public boolean hasLocalTool(String toolName) {
        return toolName != null && localTools.contains(toolName);
    }

    public boolean hasSkill(String skillName) {
        return skillName != null && skills.contains(skillName);
    }

    public boolean hasMcpTool(String serverId, String toolName) {
        if (serverId == null || toolName == null) {
            return false;
        }
        return mcpTools.contains(serverId + "/" + toolName);
    }
}
