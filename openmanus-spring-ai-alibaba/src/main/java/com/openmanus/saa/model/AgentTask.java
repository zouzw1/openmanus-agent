package com.openmanus.saa.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentTask(
        String taskId,
        String parentTaskId,
        String assignedAgentId,
        String goal,
        List<String> dependsOn,
        List<String> contextRefs,
        WorkflowStep plannedStep,
        // New fields for multi-agent support
        int priority,
        AgentTaskStatus status,
        String result,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public AgentTask {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        priority = priority < 0 ? 0 : priority;
        status = status == null ? AgentTaskStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /**
     * Create a basic task with minimal fields
     */
    public static AgentTask of(String taskId, String goal, String assignedAgentId) {
        return new AgentTask(
                taskId, null, assignedAgentId, goal,
                List.of(), List.of(), null,
                0, AgentTaskStatus.PENDING, null, Map.of(),
                Instant.now(), null, null
        );
    }

    /**
     * Create a task with dependencies
     */
    public static AgentTask of(String taskId, String goal, String assignedAgentId, List<String> dependsOn) {
        return new AgentTask(
                taskId, null, assignedAgentId, goal,
                dependsOn, List.of(), null,
                0, AgentTaskStatus.PENDING, null, Map.of(),
                Instant.now(), null, null
        );
    }

    /**
     * Create a copy with updated status
     */
    public AgentTask withStatus(AgentTaskStatus newStatus) {
        return new AgentTask(
                taskId, parentTaskId, assignedAgentId, goal,
                dependsOn, contextRefs, plannedStep,
                priority, newStatus, result, metadata,
                createdAt, startedAt, completedAt
        );
    }

    /**
     * Create a copy with updated result
     */
    public AgentTask withResult(String newResult) {
        return new AgentTask(
                taskId, parentTaskId, assignedAgentId, goal,
                dependsOn, contextRefs, plannedStep,
                priority, status, newResult, metadata,
                createdAt, startedAt, Instant.now()
        );
    }

    /**
     * Create a copy with started timestamp
     */
    public AgentTask withStarted() {
        return new AgentTask(
                taskId, parentTaskId, assignedAgentId, goal,
                dependsOn, contextRefs, plannedStep,
                priority, AgentTaskStatus.RUNNING, result, metadata,
                createdAt, Instant.now(), completedAt
        );
    }
}
