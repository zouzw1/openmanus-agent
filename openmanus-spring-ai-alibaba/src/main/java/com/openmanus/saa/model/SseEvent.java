package com.openmanus.saa.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SSE event model for streaming responses.
 */
public record SseEvent(
    SseEventType type,
    String eventId,
    Instant timestamp,
    Map<String, Object> data
) {
    public SseEvent {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }

    // Factory methods

    public static SseEvent sessionStarted(String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        return new SseEvent(SseEventType.SESSION_STARTED, null, null, data);
    }

    public static SseEvent intentResolved(IntentResolution resolution) {
        Map<String, Object> data = new HashMap<>();
        data.put("routeMode", resolution.routeMode().name());
        data.put("confidence", resolution.confidence());
        if (resolution.preferredAgentId() != null) {
            data.put("preferredAgentId", resolution.preferredAgentId());
        }
        if (!resolution.planningHints().isEmpty()) {
            data.put("planningHints", resolution.planningHints());
        }
        return new SseEvent(SseEventType.INTENT_RESOLVED, null, null, data);
    }

    public static SseEvent stepStarted(int stepIndex, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("stepIndex", stepIndex);
        data.put("description", description);
        return new SseEvent(SseEventType.STEP_STARTED, null, null, data);
    }

    public static SseEvent stepCompleted(int stepIndex, String result) {
        Map<String, Object> data = new HashMap<>();
        data.put("stepIndex", stepIndex);
        data.put("result", result);
        return new SseEvent(SseEventType.STEP_COMPLETED, null, null, data);
    }

    public static SseEvent agentStarted(String agentId, String taskId, String goal) {
        Map<String, Object> data = new HashMap<>();
        data.put("agentId", agentId);
        data.put("taskId", taskId);
        data.put("goal", goal);
        return new SseEvent(SseEventType.AGENT_STARTED, null, null, data);
    }

    public static SseEvent taskCompleted(String taskId, String summary) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("summary", summary);
        return new SseEvent(SseEventType.TASK_COMPLETED, null, null, data);
    }

    public static SseEvent artifactCreated(String artifactType, String artifactPath, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("artifactType", artifactType);
        data.put("artifactPath", artifactPath);
        data.put("description", description);
        return new SseEvent(SseEventType.ARTIFACT_CREATED, null, null, data);
    }

    public static SseEvent executionCompleted(String mode, String summary, AgentResponse response) {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", mode);
        data.put("summary", summary);
        if (response != null) {
            data.put("content", response.content());
            if (response.steps() != null && !response.steps().isEmpty()) {
                data.put("steps", response.steps());
            }
            if (response.artifacts() != null && !response.artifacts().isEmpty()) {
                data.put("artifacts", response.artifacts());
            }
        }
        return new SseEvent(SseEventType.EXECUTION_COMPLETED, null, null, data);
    }

    public static SseEvent executionCompleted(String mode, String summary) {
        return executionCompleted(mode, summary, null);
    }

    public static SseEvent error(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return new SseEvent(SseEventType.ERROR, null, null, data);
    }

    public static SseEvent error(String message, Throwable cause) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        if (cause != null) {
            data.put("cause", cause.getMessage());
            data.put("causeType", cause.getClass().getSimpleName());
        }
        return new SseEvent(SseEventType.ERROR, null, null, data);
    }

    // Builder for custom events
    public static Builder builder(SseEventType type) {
        return new Builder(type);
    }

    public static class Builder {
        private final SseEventType type;
        private String eventId;
        private Instant timestamp;
        private final Map<String, Object> data = new LinkedHashMap<>();

        public Builder(SseEventType type) {
            this.type = type;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data.putAll(data);
            return this;
        }

        public SseEvent build() {
            return new SseEvent(type, eventId, timestamp, data);
        }
    }
}
