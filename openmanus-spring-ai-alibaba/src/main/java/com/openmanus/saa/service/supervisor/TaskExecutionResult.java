package com.openmanus.saa.service.supervisor;

/**
 * Result of executing a single task.
 */
public record TaskExecutionResult(
        String taskId,
        String peerId,
        ExecutionStatus status,
        String output,
        String error,
        long executionTimeMs
) {

    public TaskExecutionResult {
        status = status == null ? ExecutionStatus.SUCCESS : status;
    }

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        /**
         * Task completed successfully
         */
        SUCCESS,
        /**
         * Task completed with partial results
         */
        PARTIAL_SUCCESS,
        /**
         * Task failed
         */
        FAILURE,
        /**
         * Task was skipped
         */
        SKIPPED,
        /**
         * Task timed out
         */
        TIMEOUT
    }

    /**
     * Create a successful result.
     *
     * @param taskId the task ID
     * @param peerId the peer ID
     * @param output the output
     * @param executionTimeMs execution time in milliseconds
     * @return the result
     */
    public static TaskExecutionResult success(String taskId, String peerId, String output, long executionTimeMs) {
        return new TaskExecutionResult(taskId, peerId, ExecutionStatus.SUCCESS, output, null, executionTimeMs);
    }

    /**
     * Create a failure result.
     *
     * @param taskId the task ID
     * @param peerId the peer ID
     * @param error the error message
     * @param executionTimeMs execution time in milliseconds
     * @return the result
     */
    public static TaskExecutionResult failure(String taskId, String peerId, String error, long executionTimeMs) {
        return new TaskExecutionResult(taskId, peerId, ExecutionStatus.FAILURE, null, error, executionTimeMs);
    }

    /**
     * Create a timeout result.
     *
     * @param taskId the task ID
     * @param peerId the peer ID
     * @param executionTimeMs execution time in milliseconds
     * @return the result
     */
    public static TaskExecutionResult timeout(String taskId, String peerId, long executionTimeMs) {
        return new TaskExecutionResult(taskId, peerId, ExecutionStatus.TIMEOUT, null, "Task timed out", executionTimeMs);
    }

    /**
     * Create a skipped result.
     *
     * @param taskId the task ID
     * @param peerId the peer ID
     * @param reason the reason for skipping
     * @return the result
     */
    public static TaskExecutionResult skipped(String taskId, String peerId, String reason) {
        return new TaskExecutionResult(taskId, peerId, ExecutionStatus.SKIPPED, null, reason, 0);
    }

    /**
     * Check if the task was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.PARTIAL_SUCCESS;
    }
}
