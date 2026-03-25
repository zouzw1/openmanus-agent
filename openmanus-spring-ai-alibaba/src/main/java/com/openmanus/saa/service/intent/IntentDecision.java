package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentRouteMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntentDecision {

    private static final IntentDecision EMPTY = new IntentDecision(
            null,
            null,
            null,
            null,
            Map.of(),
            List.of()
    );

    private final String intentId;
    private final Double confidence;
    private final IntentRouteMode routeMode;
    private final String preferredAgentId;
    private final Map<String, Object> attributes;
    private final List<String> planningHints;

    private IntentDecision(
            String intentId,
            Double confidence,
            IntentRouteMode routeMode,
            String preferredAgentId,
            Map<String, Object> attributes,
            List<String> planningHints
    ) {
        this.intentId = normalize(intentId);
        this.confidence = confidence == null ? null : Math.max(0.0d, Math.min(1.0d, confidence));
        this.routeMode = routeMode;
        this.preferredAgentId = normalize(preferredAgentId);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        this.planningHints = planningHints == null ? List.of() : List.copyOf(planningHints);
    }

    public static IntentDecision empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String intentId() {
        return intentId;
    }

    public Double confidence() {
        return confidence;
    }

    public IntentRouteMode routeMode() {
        return routeMode;
    }

    public String preferredAgentId() {
        return preferredAgentId;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public List<String> planningHints() {
        return planningHints;
    }

    public boolean isEmpty() {
        return intentId == null
                && confidence == null
                && routeMode == null
                && preferredAgentId == null
                && attributes.isEmpty()
                && planningHints.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {
        private String intentId;
        private Double confidence;
        private IntentRouteMode routeMode;
        private String preferredAgentId;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final List<String> planningHints = new ArrayList<>();

        public Builder intentId(String intentId) {
            this.intentId = intentId;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder routeMode(IntentRouteMode routeMode) {
            this.routeMode = routeMode;
            return this;
        }

        public Builder preferredAgentId(String preferredAgentId) {
            this.preferredAgentId = preferredAgentId;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder planningHints(List<String> planningHints) {
            this.planningHints.clear();
            if (planningHints != null) {
                planningHints.stream()
                        .filter(hint -> hint != null && !hint.isBlank())
                        .forEach(this.planningHints::add);
            }
            return this;
        }

        public Builder planningHint(String planningHint) {
            if (planningHint != null && !planningHint.isBlank()) {
                this.planningHints.add(planningHint);
            }
            return this;
        }

        public IntentDecision build() {
            return new IntentDecision(
                    intentId,
                    confidence,
                    routeMode,
                    preferredAgentId,
                    attributes,
                    planningHints
            );
        }
    }
}
