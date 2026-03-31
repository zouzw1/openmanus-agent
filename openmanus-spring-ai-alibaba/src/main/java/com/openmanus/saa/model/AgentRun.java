package com.openmanus.saa.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentRun(
        String runId,
        String parentSessionId,
        String objective,
        List<AgentTask> tasks,
        Instant createdAt,
        // New fields for multi-agent support
        RunStatus status,
        Map<String, String> taskResults,
        Instant startedAt,
        Instant completedAt
) {
    public AgentRun {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        status = status == null ? RunStatus.PENDING : status;
        taskResults = taskResults == null ? Map.of() : Map.copyOf(taskResults);
    }

    /**
     * Create a basic run
     */
    public static AgentRun of(String runId, String sessionId, String objective) {
        return new AgentRun(runId, sessionId, objective, List.of(), Instant.now(), RunStatus.PENDING, Map.of(), null, null);
    }

    /**
     * Create a run with tasks
     */
    public static AgentRun of(String runId, String sessionId, String objective, List<AgentTask> tasks) {
        return new AgentRun(runId, sessionId, objective, tasks, Instant.now(), RunStatus.PENDING, Map.of(), null, null);
    }

    /**
     * Create a copy with updated status
     */
    public AgentRun withStatus(RunStatus newStatus) {
        return new AgentRun(runId, parentSessionId, objective, tasks, createdAt, newStatus, taskResults, startedAt, completedAt);
    }

    /**
     * Create a copy with started timestamp
     */
    public AgentRun withStarted() {
        return new AgentRun(runId, parentSessionId, objective, tasks, createdAt, RunStatus.RUNNING, taskResults, Instant.now(), completedAt);
    }

    /**
     * Create a copy with completed timestamp
     */
    public AgentRun withCompleted() {
        return new AgentRun(runId, parentSessionId, objective, tasks, createdAt, RunStatus.COMPLETED, taskResults, startedAt, Instant.now());
    }

    /**
     * Create a copy with task results
     */
    public AgentRun withTaskResult(String taskId, String result) {
        Map<String, String> newResults = new java.util.HashMap<>(taskResults);
        newResults.put(taskId, result);
        return new AgentRun(runId, parentSessionId, objective, tasks, createdAt, status, Map.copyOf(newResults), startedAt, completedAt);
    }

    /**
     * Run status enum
     */
    public enum RunStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        PARTIALLY_COMPLETED
    }
}
