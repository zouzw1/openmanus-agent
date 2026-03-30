package com.openmanus.saa.agent;

public enum AgentRagMode {
    OFF,
    TOOL,
    ADVISOR,
    HYBRID;

    public static AgentRagMode fromValue(String rawValue, AgentRagMode defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue == null ? OFF : defaultValue;
        }
        String normalized = rawValue.trim().replace('-', '_').toUpperCase();
        return switch (normalized) {
            case "OFF", "DISABLED", "NONE" -> OFF;
            case "TOOL", "TOOLS", "TOOL_ONLY" -> TOOL;
            case "ADVISOR", "AUTO", "AUTO_RETRIEVAL" -> ADVISOR;
            case "HYBRID", "BOTH" -> HYBRID;
            default -> defaultValue == null ? OFF : defaultValue;
        };
    }

    public boolean usesTools() {
        return this == TOOL || this == HYBRID;
    }

    public boolean usesAdvisor() {
        return this == ADVISOR || this == HYBRID;
    }
}
