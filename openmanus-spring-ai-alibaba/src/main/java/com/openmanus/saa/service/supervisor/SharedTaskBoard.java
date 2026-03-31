package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared task board for managing tasks and their dependencies.
 * Supports task status tracking, dependency resolution, and parallel execution.
 */
public class SharedTaskBoard {

    private static final Logger log = LoggerFactory.getLogger(SharedTaskBoard.class);

    private final String runId;
    private final Map<String, AgentTask> tasks;
    private final Map<String, AgentTaskStatus> taskStatuses;
    private final Map<String, String> taskResults;
    private final Map<String, String> taskErrors;
    private final Map<String, Instant> taskStartTimes;
    private final Map<String, Instant> taskEndTimes;

    public SharedTaskBoard(String runId) {
        this.runId = runId;
        this.tasks = new ConcurrentHashMap<>();
        this.taskStatuses = new ConcurrentHashMap<>();
        this.taskResults = new ConcurrentHashMap<>();
        this.taskErrors = new ConcurrentHashMap<>();
        this.taskStartTimes = new ConcurrentHashMap<>();
        this.taskEndTimes = new ConcurrentHashMap<>();
    }

    /**
     * Get the run ID for this task board.
     *
     * @return the run ID
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Add a task to the board.
     *
     * @param task the task to add
     */
    public void addTask(AgentTask task) {
        tasks.put(task.taskId(), task);
        taskStatuses.put(task.taskId(), task.status() != null ? task.status() : AgentTaskStatus.PENDING);
        log.debug("Added task {} to board {}", task.taskId(), runId);
    }

    /**
     * Add multiple tasks to the board.
     *
     * @param taskList the tasks to add
     */
    public void addTasks(List<AgentTask> taskList) {
        taskList.forEach(this::addTask);
    }

    /**
     * Get a task by ID.
     *
     * @param taskId the task ID
     * @return the task or empty if not found
     */
    public Optional<AgentTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Get all tasks.
     *
     * @return unmodifiable list of all tasks
     */
    public List<AgentTask> getAllTasks() {
        return Collections.unmodifiableList(new ArrayList<>(tasks.values()));
    }

    /**
     * Get task status.
     *
     * @param taskId the task ID
     * @return the status or empty if not found
     */
    public Optional<AgentTaskStatus> getTaskStatus(String taskId) {
        return Optional.ofNullable(taskStatuses.get(taskId));
    }

    /**
     * Get task result.
     *
     * @param taskId the task ID
     * @return the result or empty if not found
     */
    public Optional<String> getTaskResult(String taskId) {
        return Optional.ofNullable(taskResults.get(taskId));
    }

    /**
     * Get task error.
     *
     * @param taskId the task ID
     * @return the error or empty if not found
     */
    public Optional<String> getTaskError(String taskId) {
        return Optional.ofNullable(taskErrors.get(taskId));
    }

    /**
     * Get all task results.
     *
     * @return unmodifiable map of task ID to result
     */
    public Map<String, String> getTaskResults() {
        return Collections.unmodifiableMap(taskResults);
    }

    /**
     * Get tasks that are ready to execute (dependencies satisfied and not yet started).
     *
     * @return list of ready tasks, sorted by priority (highest first)
     */
    public List<AgentTask> getReadyTasks() {
        return tasks.values().stream()
                .filter(task -> taskStatuses.get(task.taskId()) == AgentTaskStatus.PENDING)
                .filter(this::areDependenciesMet)
                .sorted(Comparator.comparingInt(AgentTask::priority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Mark a task as started (RUNNING).
     *
     * @param taskId the task ID
     * @return true if successfully marked
     */
    public boolean markStarted(String taskId) {
        AgentTaskStatus currentStatus = taskStatuses.get(taskId);
        if (currentStatus == AgentTaskStatus.PENDING) {
            taskStatuses.put(taskId, AgentTaskStatus.RUNNING);
            taskStartTimes.put(taskId, Instant.now());
            log.debug("Task {} marked as RUNNING", taskId);
            return true;
        }
        return false;
    }

    /**
     * Mark a task as completed with a result.
     *
     * @param taskId the task ID
     * @param result the execution result
     */
    public void markCompleted(String taskId, String result) {
        taskStatuses.put(taskId, AgentTaskStatus.COMPLETED);
        taskResults.put(taskId, result);
        taskEndTimes.put(taskId, Instant.now());
        log.debug("Task {} marked as COMPLETED", taskId);
    }

    /**
     * Mark a task as failed with an error message.
     *
     * @param taskId the task ID
     * @param error the error message
     */
    public void markFailed(String taskId, String error) {
        taskStatuses.put(taskId, AgentTaskStatus.FAILED);
        taskErrors.put(taskId, error);
        taskEndTimes.put(taskId, Instant.now());
        log.debug("Task {} marked as FAILED: {}", taskId, error);
    }

    /**
     * Check if all dependencies of a task are met.
     *
     * @param task the task to check
     * @return true if all dependencies are completed
     */
    public boolean areDependenciesMet(AgentTask task) {
        if (task.dependsOn() == null || task.dependsOn().isEmpty()) {
            return true;
        }
        return task.dependsOn().stream()
                .allMatch(depId -> taskStatuses.get(depId) == AgentTaskStatus.COMPLETED);
    }

    /**
     * Check if all tasks are completed (successfully or failed).
     *
     * @return true if all tasks are done
     */
    public boolean isAllCompleted() {
        return tasks.values().stream()
                .allMatch(task -> {
                    AgentTaskStatus status = taskStatuses.get(task.taskId());
                    return status == AgentTaskStatus.COMPLETED ||
                            status == AgentTaskStatus.FAILED ||
                            status == AgentTaskStatus.NEEDS_HUMAN_FEEDBACK;
                });
    }

    /**
     * Check if any task has failed.
     *
     * @return true if any task failed
     */
    public boolean hasFailedTasks() {
        return taskStatuses.containsValue(AgentTaskStatus.FAILED);
    }

    /**
     * Check if there are blocked tasks (dependencies cannot be satisfied).
     *
     * @return true if there are blocked tasks
     */
    public boolean hasBlockedTasks() {
        return tasks.values().stream()
                .filter(task -> taskStatuses.get(task.taskId()) == AgentTaskStatus.PENDING)
                .anyMatch(task -> !areDependenciesMet(task) && areDependenciesFailed(task));
    }

    /**
     * Check if dependencies of a task have failed.
     *
     * @param task the task to check
     * @return true if any dependency has failed
     */
    private boolean areDependenciesFailed(AgentTask task) {
        if (task.dependsOn() == null || task.dependsOn().isEmpty()) {
            return false;
        }
        return task.dependsOn().stream()
                .anyMatch(depId -> taskStatuses.get(depId) == AgentTaskStatus.FAILED);
    }

    /**
     * Get failed tasks.
     *
     * @return list of failed tasks
     */
    public List<AgentTask> getFailedTasks() {
        return tasks.values().stream()
                .filter(task -> taskStatuses.get(task.taskId()) == AgentTaskStatus.FAILED)
                .collect(Collectors.toList());
    }

    /**
     * Get completed tasks.
     *
     * @return list of completed tasks
     */
    public List<AgentTask> getCompletedTasks() {
        return tasks.values().stream()
                .filter(task -> taskStatuses.get(task.taskId()) == AgentTaskStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /**
     * Get task start time.
     *
     * @param taskId the task ID
     * @return the start time or empty
     */
    public Optional<Instant> getTaskStartTime(String taskId) {
        return Optional.ofNullable(taskStartTimes.get(taskId));
    }

    /**
     * Get task end time.
     *
     * @param taskId the task ID
     * @return the end time or empty
     */
    public Optional<Instant> getTaskEndTime(String taskId) {
        return Optional.ofNullable(taskEndTimes.get(taskId));
    }

    /**
     * Get summary statistics.
     *
     * @return map of status to count
     */
    public Map<AgentTaskStatus, Long> getStatusSummary() {
        return taskStatuses.values().stream()
                .collect(Collectors.groupingBy(status -> status, Collectors.counting()));
    }

    /**
     * Clear all tasks from the board.
     */
    public void clear() {
        tasks.clear();
        taskStatuses.clear();
        taskResults.clear();
        taskErrors.clear();
        taskStartTimes.clear();
        taskEndTimes.clear();
    }

    /**
     * Get the number of tasks on the board.
     *
     * @return the task count
     */
    public int size() {
        return tasks.size();
    }
}
