package com.openmanus.saa.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record IntentResolution(
        String intentId,
        double confidence,
        IntentRouteMode routeMode,
        String preferredAgentId,
        Map<String, Object> attributes,
        List<String> planningHints
) {
    public IntentResolution {
        intentId = intentId == null || intentId.isBlank() ? "general" : intentId.trim();
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        routeMode = routeMode == null ? IntentRouteMode.PLAN_EXECUTE : routeMode;
        preferredAgentId = preferredAgentId == null || preferredAgentId.isBlank() ? null : preferredAgentId.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        planningHints = planningHints == null ? List.of() : List.copyOf(planningHints);
    }
}
