package com.openmanus.saa.agent;

public enum CapabilityAccessMode {
    ALLOW_ALL,
    ALLOW_LIST,
    DENY_ALL;

    public static CapabilityAccessMode fromValue(String value, CapabilityAccessMode defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "allow_all" -> ALLOW_ALL;
            case "deny_all" -> DENY_ALL;
            default -> ALLOW_LIST;
        };
    }
}
