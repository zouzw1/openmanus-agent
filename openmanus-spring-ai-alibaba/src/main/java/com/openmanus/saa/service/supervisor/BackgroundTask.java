package com.openmanus.saa.service.supervisor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 后台任务记录，用于追踪异步执行的任务状态。
 */
public record BackgroundTask(
    /**
     * 任务ID
     */
    String taskId,

    /**
     * 会话ID
     */
    String sessionId,

    /**
     * Agent类型
     */
    String agentType,

    /**
     * 执行Future
     */
    CompletableFuture<TaskExecutionResult> future,

    /**
     * 开始时间
     */
    Instant startTime,

    /**
     * 任务分发选项
     */
    TaskDispatchOptionsSnapshot options,

    /**
     * 任务目标描述
     */
    String objective
) {
    /**
     * 选项快照（避免引用复杂对象）
     */
    public record TaskDispatchOptionsSnapshot(
        boolean background,
        String isolation,
        String model,
        int maxTurns,
        String mode
    ) {}

    /**
     * 检查任务是否完成
     */
    public boolean isCompleted() {
        return future != null && future.isDone();
    }

    /**
     * 检查任务是否失败
     */
    public boolean isFailed() {
        return future != null && future.isCompletedExceptionally();
    }

    /**
     * 检查任务是否取消
     */
    public boolean isCancelled() {
        return future != null && future.isCancelled();
    }

    /**
     * 获取执行时长（毫秒）
     */
    public long getDurationMs() {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }

    /**
     * 获取结果（非阻塞）
     */
    public TaskExecutionResult getResultNow() {
        if (future == null || !future.isDone()) {
            return null;
        }
        try {
            return future.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }
}
