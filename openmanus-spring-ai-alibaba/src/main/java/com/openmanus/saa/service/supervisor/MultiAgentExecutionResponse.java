package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentTaskStatus;

import java.util.List;
import java.util.Map;

/**
 * Response from multi-agent execution.
 */
public record MultiAgentExecutionResponse(
        String runId,
        String sessionId,
        String objective,
        ExecutionStatus status,
        List<AgentTask> tasks,
        Map<String, String> taskResults,
        String aggregatedOutput,
        Map<AgentTaskStatus, Long> statusSummary,
        String error
) {

    public MultiAgentExecutionResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        taskResults = taskResults == null ? Map.of() : Map.copyOf(taskResults);
        statusSummary = statusSummary == null ? Map.of() : Map.copyOf(statusSummary);
    }

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        /**
         * Execution is disabled
         */
        DISABLED,
        /**
         * Execution is running
         */
        RUNNING,
        /**
         * Execution completed successfully
         */
        COMPLETED,
        /**
         * Execution completed with partial results
         */
        PARTIALLY_COMPLETED,
        /**
         * Execution failed
         */
        FAILED
    }

    /**
     * Create a disabled response.
     *
     * @param message the reason message
     * @return the response
     */
    public static MultiAgentExecutionResponse disabled(String message) {
        return new MultiAgentExecutionResponse(
                null, null, null, ExecutionStatus.DISABLED,
                List.of(), Map.of(), null, Map.of(), message
        );
    }

    /**
     * Create a failure response.
     *
     * @param runId the run ID
     * @param error the error message
     * @return the response
     */
    public static MultiAgentExecutionResponse failure(String runId, String error) {
        return new MultiAgentExecutionResponse(
                runId, null, null, ExecutionStatus.FAILED,
                List.of(), Map.of(), null, Map.of(), error
        );
    }

    /**
     * Check if execution was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.COMPLETED || status == ExecutionStatus.PARTIALLY_COMPLETED;
    }

    /**
     * Check if execution is disabled.
     *
     * @return true if disabled
     */
    public boolean isDisabled() {
        return status == ExecutionStatus.DISABLED;
    }

    /**
     * Get the number of completed tasks.
     *
     * @return the count
     */
    public long getCompletedCount() {
        return statusSummary.getOrDefault(AgentTaskStatus.COMPLETED, 0L);
    }

    /**
     * Get the number of failed tasks.
     *
     * @return the count
     */
    public long getFailedCount() {
        return statusSummary.getOrDefault(AgentTaskStatus.FAILED, 0L);
    }

    /**
     * Get the total number of tasks.
     *
     * @return the count
     */
    public int getTotalTasks() {
        return tasks.size();
    }
}
