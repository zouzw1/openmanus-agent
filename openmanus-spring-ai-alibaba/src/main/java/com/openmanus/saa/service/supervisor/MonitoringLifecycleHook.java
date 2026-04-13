package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentType;
import com.openmanus.saa.service.supervisor.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitoring lifecycle hook implementation.
 * Records all agent activities for observability and debugging.
 */
@Component
public class MonitoringLifecycleHook implements AgentLifecycleHook {

    private static final Logger log = LoggerFactory.getLogger(MonitoringLifecycleHook.class);

    // 统计计数器
    private final AtomicLong tasksStarted = new AtomicLong(0);
    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksFailed = new AtomicLong(0);
    private final AtomicLong backgroundTasksStarted = new AtomicLong(0);
    private final AtomicLong backgroundTasksCompleted = new AtomicLong(0);
    private final AtomicLong contextSummarizations = new AtomicLong(0);
    private final AtomicLong toolAccessDenials = new AtomicLong(0);

    // 活跃任务追踪
    private final Map<String, Instant> activeTasks = new ConcurrentHashMap<>();

    @Override
    public void onAgentStarted(AgentPeer peer) {
        log.info("[Monitor] Agent started: {} (type: {})", peer.getPeerId(), peer.getAgentId());
    }

    @Override
    public void onAgentStopped(AgentPeer peer) {
        log.info("[Monitor] Agent stopped: {}", peer.getPeerId());
    }

    @Override
    public void onTaskAssigned(AgentPeer peer, AgentTask task) {
        log.debug("[Monitor] Task assigned to {}: taskId={}", peer.getPeerId(), task.taskId());
    }

    @Override
    public void onTaskStarted(AgentPeer peer, AgentTask task) {
        tasksStarted.incrementAndGet();
        activeTasks.put(task.taskId(), Instant.now());
        log.info("[Monitor] Task started: {} by agent {}", task.taskId(), peer.getPeerId());
    }

    @Override
    public void onTaskCompleted(AgentPeer peer, AgentTask task, TaskExecutionResult result) {
        tasksCompleted.incrementAndGet();
        Instant startTime = activeTasks.remove(task.taskId());
        long durationMs = startTime != null
            ? java.time.Duration.between(startTime, Instant.now()).toMillis()
            : result.executionTimeMs();

        log.info("[Monitor] Task completed: {} by agent {} in {}ms (success={})",
            task.taskId(), peer.getPeerId(), durationMs, result.isSuccess());
    }

    @Override
    public void onError(AgentPeer peer, AgentTask task, Throwable error) {
        tasksFailed.incrementAndGet();
        if (task != null) {
            activeTasks.remove(task.taskId());
        }
        log.error("[Monitor] Error in agent {}: {}", peer.getPeerId(), error.getMessage());
    }

    @Override
    public void onContextSummarized(AgentPeer peer, int originalLength, int summaryLength, String summary) {
        contextSummarizations.incrementAndGet();
        double compressionRatio = (double) summaryLength / originalLength;
        log.info("[Monitor] Context summarized for {}: {} -> {} chars ({}% compression)",
            peer.getPeerId(), originalLength, summaryLength, String.format("%.1f", compressionRatio * 100));
    }

    @Override
    public void onBackgroundTaskStarted(AgentPeer peer, String taskId, AgentType agentType) {
        backgroundTasksStarted.incrementAndGet();
        activeTasks.put(taskId, Instant.now());
        log.info("[Monitor] Background task started: {} by {} agent {}", taskId, agentType, peer.getPeerId());
    }

    @Override
    public void onBackgroundTaskCompleted(AgentPeer peer, String taskId, TaskExecutionResult result) {
        backgroundTasksCompleted.incrementAndGet();
        Instant startTime = activeTasks.remove(taskId);
        long durationMs = startTime != null
            ? java.time.Duration.between(startTime, Instant.now()).toMillis()
            : 0;

        log.info("[Monitor] Background task completed: {} in {}ms (success={})",
            taskId, durationMs, result.isSuccess());
    }

    @Override
    public void onBackgroundTaskFailed(AgentPeer peer, String taskId, Throwable error, boolean cancelled) {
        tasksFailed.incrementAndGet();
        activeTasks.remove(taskId);
        log.warn("[Monitor] Background task {} {} - {}",
            taskId, cancelled ? "cancelled" : "failed",
            error != null ? error.getMessage() : "unknown error");
    }

    @Override
    public void onToolAccessDenied(AgentPeer peer, String toolName, String reason) {
        toolAccessDenials.incrementAndGet();
        log.warn("[Monitor] Tool access denied for {}: {} - {}", peer.getPeerId(), toolName, reason);
    }

    @Override
    public void onIsolationActivated(AgentPeer peer, String isolationType, String isolatedPath) {
        log.info("[Monitor] Isolation activated for {}: {} at {}", peer.getPeerId(), isolationType, isolatedPath);
    }

    @Override
    public int getOrder() {
        // 监控钩子应该最后执行
        return Integer.MAX_VALUE;
    }

    // ================== 统计查询方法 ==================

    public long getTasksStarted() {
        return tasksStarted.get();
    }

    public long getTasksCompleted() {
        return tasksCompleted.get();
    }

    public long getTasksFailed() {
        return tasksFailed.get();
    }

    public long getBackgroundTasksStarted() {
        return backgroundTasksStarted.get();
    }

    public long getBackgroundTasksCompleted() {
        return backgroundTasksCompleted.get();
    }

    public long getContextSummarizations() {
        return contextSummarizations.get();
    }

    public long getToolAccessDenials() {
        return toolAccessDenials.get();
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    /**
     * 获取统计摘要。
     */
    public Map<String, Long> getStatistics() {
        return Map.of(
            "tasksStarted", tasksStarted.get(),
            "tasksCompleted", tasksCompleted.get(),
            "tasksFailed", tasksFailed.get(),
            "backgroundTasksStarted", backgroundTasksStarted.get(),
            "backgroundTasksCompleted", backgroundTasksCompleted.get(),
            "contextSummarizations", contextSummarizations.get(),
            "toolAccessDenials", toolAccessDenials.get(),
            "activeTasks", (long) activeTasks.size()
        );
    }

    /**
     * 重置统计计数器。
     */
    public void resetStatistics() {
        tasksStarted.set(0);
        tasksCompleted.set(0);
        tasksFailed.set(0);
        backgroundTasksStarted.set(0);
        backgroundTasksCompleted.set(0);
        contextSummarizations.set(0);
        toolAccessDenials.set(0);
        activeTasks.clear();
        log.info("[Monitor] Statistics reset");
    }
}
