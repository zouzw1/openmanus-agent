package com.openmanus.saa.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增强的 Workflow Step，包含完整的状态管理信息
 */
public class WorkflowStep {
    private final String agent;
    private final String description;
    private List<String> requiredTools;
    private Map<String, Object> parameterContext;
    private StepStatus status;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private Integer attemptCount;
    private boolean needsHumanFeedback;

    // 向后兼容的构造函数 - 初始状态
    public WorkflowStep(String agent, String description) {
        this.agent = agent;
        this.description = description;
        this.requiredTools = new ArrayList<>();
        this.parameterContext = new HashMap<>();
        this.status = StepStatus.NOT_STARTED;
        this.result = null;
        this.startTime = null;
        this.endTime = null;
        this.errorMessage = null;
        this.attemptCount = 0;
        this.needsHumanFeedback = false;
    }
    
    // 便捷构造函数 - 带工具和参数（用于 PlanningService）
    public WorkflowStep(String agent, String description, List<String> requiredTools, Map<String, Object> parameterContext) {
        this.agent = agent;
        this.description = description;
        this.requiredTools = requiredTools != null ? requiredTools : new ArrayList<>();
        this.parameterContext = parameterContext != null ? parameterContext : new HashMap<>();
        this.status = StepStatus.NOT_STARTED;
        this.result = null;
        this.startTime = null;
        this.endTime = null;
        this.errorMessage = null;
        this.attemptCount = 0;
        this.needsHumanFeedback = false;
    }

    // 全参数构造函数
    public WorkflowStep(String agent, String description, List<String> requiredTools,
                       Map<String, Object> parameterContext, StepStatus status, String result,
                       LocalDateTime startTime, LocalDateTime endTime, String errorMessage,
                       Integer attemptCount, boolean needsHumanFeedback) {
        this.agent = agent;
        this.description = description;
        this.requiredTools = requiredTools != null ? requiredTools : new ArrayList<>();
        this.parameterContext = parameterContext != null ? parameterContext : new HashMap<>();
        this.status = status != null ? status : StepStatus.NOT_STARTED;
        this.result = result;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount != null ? attemptCount : 0;
        this.needsHumanFeedback = needsHumanFeedback;
    }

    // Getters
    public String getAgent() { return agent; }
    public String getDescription() { return description; }
    public List<String> getRequiredTools() { return requiredTools; }
    public Map<String, Object> getParameterContext() { return parameterContext; }
    public StepStatus getStatus() { return status; }
    public String getResult() { return result; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getAttemptCount() { return attemptCount; }
    public boolean isNeedsHumanFeedback() { return needsHumanFeedback; }
    
    // Record-style accessors for backward compatibility (与 record 兼容)
    public String agent() { return getAgent(); }
    public String description() { return getDescription(); }
    public List<String> requiredTools() { return getRequiredTools(); }
    public Map<String, Object> parameterContext() { return getParameterContext(); }
    public StepStatus status() { return getStatus(); }
    public String result() { return getResult(); }
    public LocalDateTime startTime() { return getStartTime(); }
    public LocalDateTime endTime() { return getEndTime(); }
    public String errorMessage() { return getErrorMessage(); }
    public Integer attemptCount() { return getAttemptCount(); }
    public boolean needsHumanFeedback() { return isNeedsHumanFeedback(); }

    /**
     * 获取所需的第一个工具（主要工具）
     */
    public String primaryTool() {
        return (requiredTools != null && !requiredTools.isEmpty())
            ? requiredTools.get(0)
            : null;
    }

    /**
     * 创建当前步骤的副本并更新状态
     */
    public WorkflowStep withStatus(StepStatus newStatus) {
        return new WorkflowStep(
            this.agent, this.description, this.requiredTools, this.parameterContext,
            newStatus, this.result, this.startTime, this.endTime,
            this.errorMessage, this.attemptCount, this.needsHumanFeedback
        );
    }

    /**
     * 创建当前步骤的副本并更新结果
     */
    public WorkflowStep withResult(String newResult) {
        return new WorkflowStep(
            this.agent, this.description, this.requiredTools, this.parameterContext,
            StepStatus.COMPLETED, newResult, this.startTime, LocalDateTime.now(),
            null, this.attemptCount, false
        );
    }

    /**
     * 创建当前步骤的副本并标记需要人工反馈
     */
    public WorkflowStep withHumanFeedbackNeeded(String error) {
        return new WorkflowStep(
            this.agent, this.description, this.requiredTools, this.parameterContext,
            StepStatus.FAILED_NEEDS_HUMAN_INTERVENTION, null, this.startTime, null,
            error, this.attemptCount + 1, true
        );
    }

    /**
     * 创建当前步骤的副本并增加尝试次数
     */
    public WorkflowStep withRetry() {
        return new WorkflowStep(
            this.agent, this.description, this.requiredTools, this.parameterContext,
            StepStatus.IN_PROGRESS, null, this.startTime, null,
            null, this.attemptCount + 1, false
        );
    }

    /**
     * 判断步骤是否已完成
     */
    public boolean isCompleted() {
        return this.status == StepStatus.COMPLETED || this.status == StepStatus.SKIPPED;
    }

    /**
     * 判断步骤是否需要人工介入
     */
    public boolean needsHumanIntervention() {
        return this.status.needsHumanIntervention() || this.needsHumanFeedback;
    }

    @Override
    public String toString() {
        return String.format("WorkflowStep{agent='%s', desc='%s', status=%s, attempts=%d}",
            agent, description, status, attemptCount);
    }
}
