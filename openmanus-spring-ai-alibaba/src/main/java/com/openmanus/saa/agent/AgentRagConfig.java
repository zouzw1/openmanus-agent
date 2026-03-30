package com.openmanus.saa.agent;

import java.util.LinkedHashSet;
import java.util.Set;

public class AgentRagConfig {

    private static final AgentRagConfig DISABLED = new AgentRagConfig(false, AgentRagMode.OFF, Set.of(), null);

    private final boolean enabled;
    private final AgentRagMode mode;
    private final Set<String> knowledgeBaseIds;
    private final Integer topK;

    public AgentRagConfig(boolean enabled, AgentRagMode mode, Set<String> knowledgeBaseIds, Integer topK) {
        this.enabled = enabled;
        this.mode = enabled ? (mode == null ? AgentRagMode.TOOL : mode) : AgentRagMode.OFF;
        this.knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(knowledgeBaseIds));
        this.topK = topK != null && topK > 0 ? topK : null;
    }

    public static AgentRagConfig disabled() {
        return DISABLED;
    }

    public boolean isEnabled() {
        return enabled && mode != AgentRagMode.OFF;
    }

    public AgentRagMode getMode() {
        return mode;
    }

    public Set<String> getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public Integer getTopK() {
        return topK;
    }

    public boolean usesTools() {
        return isEnabled() && mode.usesTools();
    }

    public boolean usesAdvisor() {
        return isEnabled() && mode.usesAdvisor();
    }
}
