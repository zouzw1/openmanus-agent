package com.openmanus.saa.model.context;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.WorkflowStep;
import java.time.Instant;
import java.util.List;

/**
 * 工作流状态，用于会话级持久化和恢复。
 */
public record WorkflowState(
    WorkflowStatus status,
    String objective,
    String planId,
    int currentStepIndex,
    List<WorkflowStep> steps,
    HumanFeedbackRequest pendingFeedback,
    String lastDeliverable,
    Instant lastActiveAt
) {
    public enum WorkflowStatus {
        NONE,           // 无工作流
        IN_PROGRESS,    // 执行中
        PAUSED,         // 暂停等待反馈
        COMPLETED,      // 已完成
        FAILED          // 失败
    }

    public static WorkflowState none() {
        return new WorkflowState(WorkflowStatus.NONE, null, null, 0, null, null, null, null);
    }

    public static WorkflowState inProgress(String objective, String planId, List<WorkflowStep> steps) {
        return new WorkflowState(WorkflowStatus.IN_PROGRESS, objective, planId, 0, steps, null, null, Instant.now());
    }

    public static WorkflowState paused(String objective, String planId, int stepIndex,
            List<WorkflowStep> steps, HumanFeedbackRequest pendingFeedback) {
        return new WorkflowState(WorkflowStatus.PAUSED, objective, planId, stepIndex, steps, pendingFeedback, null, Instant.now());
    }

    public static WorkflowState completed(String objective, String planId, String lastDeliverable) {
        return new WorkflowState(WorkflowStatus.COMPLETED, objective, planId, 0, null, null, lastDeliverable, Instant.now());
    }

    public static WorkflowState failed(String objective, String planId, int stepIndex, List<WorkflowStep> steps) {
        return new WorkflowState(WorkflowStatus.FAILED, objective, planId, stepIndex, steps, null, null, Instant.now());
    }

    public boolean isPaused() {
        return status == WorkflowStatus.PAUSED;
    }

    public boolean isCompleted() {
        return status == WorkflowStatus.COMPLETED;
    }

    public boolean hasActiveWorkflow() {
        return status != WorkflowStatus.NONE;
    }
}
