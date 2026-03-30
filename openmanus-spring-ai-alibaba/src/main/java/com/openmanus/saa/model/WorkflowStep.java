package com.openmanus.saa.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowStep {
    private final String agent;
    private final String description;
    private final List<String> requiredTools;
    private final List<String> usedTools;
    private final List<String> usedCapabilities;
    private final List<String> artifacts;
    private final List<String> toolOutputs;
    private final Map<String, Object> parameterContext;
    private final StepStatus status;
    private final String result;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String errorMessage;
    private final Integer attemptCount;
    private final boolean needsHumanFeedback;

    public WorkflowStep(String agent, String description) {
        this(agent, description, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), StepStatus.NOT_STARTED, null, null, null, null, 0, false);
    }

    public WorkflowStep(String agent, String description, List<String> requiredTools, Map<String, Object> parameterContext) {
        this(agent, description, requiredTools, List.of(), List.of(), List.of(), List.of(), parameterContext, StepStatus.NOT_STARTED, null, null, null, null, 0, false);
    }

    public WorkflowStep(
            String agent,
            String description,
            List<String> requiredTools,
            Map<String, Object> parameterContext,
            StepStatus status,
            String result,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String errorMessage,
            Integer attemptCount,
            boolean needsHumanFeedback
    ) {
        this(agent, description, requiredTools, List.of(), List.of(), List.of(), List.of(), parameterContext, status, result, startTime, endTime, errorMessage, attemptCount, needsHumanFeedback);
    }

    public WorkflowStep(
            String agent,
            String description,
            List<String> requiredTools,
            List<String> usedTools,
            Map<String, Object> parameterContext,
            StepStatus status,
            String result,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String errorMessage,
            Integer attemptCount,
            boolean needsHumanFeedback
    ) {
        this(
                agent,
                description,
                requiredTools,
                usedTools,
                List.of(),
                List.of(),
                List.of(),
                parameterContext,
                status,
                result,
                startTime,
                endTime,
                errorMessage,
                attemptCount,
                needsHumanFeedback
        );
    }

    @JsonCreator
    public WorkflowStep(
            @JsonProperty("agent") String agent,
            @JsonProperty("description") String description,
            @JsonProperty("requiredTools") List<String> requiredTools,
            @JsonProperty("usedTools") List<String> usedTools,
            @JsonProperty("usedCapabilities") List<String> usedCapabilities,
            @JsonProperty("artifacts") List<String> artifacts,
            @JsonProperty("toolOutputs") List<String> toolOutputs,
            @JsonProperty("parameterContext") Map<String, Object> parameterContext,
            @JsonProperty("status") StepStatus status,
            @JsonProperty("result") String result,
            @JsonProperty("startTime") LocalDateTime startTime,
            @JsonProperty("endTime") LocalDateTime endTime,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("attemptCount") Integer attemptCount,
            @JsonProperty("needsHumanFeedback") boolean needsHumanFeedback
    ) {
        this.agent = agent;
        this.description = description;
        this.requiredTools = requiredTools == null ? List.of() : new ArrayList<>(requiredTools);
        this.usedTools = usedTools == null ? List.of() : new ArrayList<>(usedTools);
        this.usedCapabilities = usedCapabilities == null ? List.of() : new ArrayList<>(usedCapabilities);
        this.artifacts = artifacts == null ? List.of() : new ArrayList<>(artifacts);
        this.toolOutputs = toolOutputs == null ? List.of() : new ArrayList<>(toolOutputs);
        this.parameterContext = parameterContext == null ? Map.of() : new HashMap<>(parameterContext);
        this.status = status == null ? StepStatus.NOT_STARTED : status;
        this.result = result;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount == null ? 0 : attemptCount;
        this.needsHumanFeedback = needsHumanFeedback;
    }

    public String getAgent() { return agent; }

    public String getDescription() { return description; }

    public List<String> getRequiredTools() { return requiredTools; }

    public List<String> getUsedTools() { return usedTools; }

    public List<String> getUsedCapabilities() { return usedCapabilities; }

    public List<String> getArtifacts() { return artifacts; }

    public List<String> getToolOutputs() { return toolOutputs; }

    public Map<String, Object> getParameterContext() { return parameterContext; }

    public StepStatus getStatus() { return status; }

    public String getResult() { return result; }

    public LocalDateTime getStartTime() { return startTime; }

    public LocalDateTime getEndTime() { return endTime; }

    public String getErrorMessage() { return errorMessage; }

    public Integer getAttemptCount() { return attemptCount; }

    public boolean isNeedsHumanFeedback() { return needsHumanFeedback; }

    public String agent() { return getAgent(); }

    public String description() { return getDescription(); }

    public List<String> requiredTools() { return getRequiredTools(); }

    public List<String> usedTools() { return getUsedTools(); }

    public List<String> usedCapabilities() { return getUsedCapabilities(); }

    public List<String> artifacts() { return getArtifacts(); }

    public List<String> toolOutputs() { return getToolOutputs(); }

    public Map<String, Object> parameterContext() { return getParameterContext(); }

    public StepStatus status() { return getStatus(); }

    public String result() { return getResult(); }

    public LocalDateTime startTime() { return getStartTime(); }

    public LocalDateTime endTime() { return getEndTime(); }

    public String errorMessage() { return getErrorMessage(); }

    public Integer attemptCount() { return getAttemptCount(); }

    public boolean needsHumanFeedback() { return isNeedsHumanFeedback(); }

    public String primaryTool() {
        return requiredTools.isEmpty() ? null : requiredTools.get(0);
    }

    public WorkflowStep withStatus(StepStatus newStatus) {
        return new WorkflowStep(
                agent,
                description,
                requiredTools,
                usedTools,
                usedCapabilities,
                artifacts,
                toolOutputs,
                parameterContext,
                newStatus,
                result,
                startTime,
                endTime,
                errorMessage,
                attemptCount,
                needsHumanFeedback
        );
    }

    public WorkflowStep withResult(String newResult) {
        return withResult(newResult, usedTools);
    }

    public WorkflowStep withResult(String newResult, List<String> newUsedTools) {
        return withResult(newResult, newUsedTools, usedCapabilities);
    }

    public WorkflowStep withResult(String newResult, List<String> newUsedTools, List<String> newUsedCapabilities) {
        return withResult(newResult, newUsedTools, newUsedCapabilities, artifacts, toolOutputs);
    }

    public WorkflowStep withResult(String newResult, List<String> newUsedTools, List<String> newUsedCapabilities, List<String> newArtifacts) {
        return withResult(newResult, newUsedTools, newUsedCapabilities, newArtifacts, toolOutputs);
    }

    public WorkflowStep withResult(
            String newResult,
            List<String> newUsedTools,
            List<String> newUsedCapabilities,
            List<String> newArtifacts,
            List<String> newToolOutputs
    ) {
        return new WorkflowStep(
                agent,
                description,
                requiredTools,
                newUsedTools,
                newUsedCapabilities,
                newArtifacts,
                newToolOutputs,
                parameterContext,
                StepStatus.COMPLETED,
                newResult,
                startTime,
                LocalDateTime.now(),
                null,
                attemptCount,
                false
        );
    }

    public WorkflowStep withHumanFeedbackNeeded(String error) {
        return withHumanFeedbackNeeded(error, usedTools);
    }

    public WorkflowStep withHumanFeedbackNeeded(String error, List<String> newUsedTools) {
        return withHumanFeedbackNeeded(error, newUsedTools, usedCapabilities);
    }

    public WorkflowStep withHumanFeedbackNeeded(String error, List<String> newUsedTools, List<String> newUsedCapabilities) {
        return withHumanFeedbackNeeded(error, newUsedTools, newUsedCapabilities, artifacts, toolOutputs);
    }

    public WorkflowStep withHumanFeedbackNeeded(String error, List<String> newUsedTools, List<String> newUsedCapabilities, List<String> newArtifacts) {
        return withHumanFeedbackNeeded(error, newUsedTools, newUsedCapabilities, newArtifacts, toolOutputs);
    }

    public WorkflowStep withHumanFeedbackNeeded(
            String error,
            List<String> newUsedTools,
            List<String> newUsedCapabilities,
            List<String> newArtifacts,
            List<String> newToolOutputs
    ) {
        return new WorkflowStep(
                agent,
                description,
                requiredTools,
                newUsedTools,
                newUsedCapabilities,
                newArtifacts,
                newToolOutputs,
                parameterContext,
                StepStatus.FAILED_NEEDS_HUMAN_INTERVENTION,
                null,
                startTime,
                null,
                error,
                attemptCount + 1,
                true
        );
    }

    public WorkflowStep withFailure(String error, List<String> newUsedTools, int attempts) {
        return withFailure(error, newUsedTools, usedCapabilities, attempts);
    }

    public WorkflowStep withFailure(String error, List<String> newUsedTools, List<String> newUsedCapabilities, int attempts) {
        return withFailure(error, newUsedTools, newUsedCapabilities, artifacts, toolOutputs, attempts);
    }

    public WorkflowStep withFailure(String error, List<String> newUsedTools, List<String> newUsedCapabilities, List<String> newArtifacts, int attempts) {
        return withFailure(error, newUsedTools, newUsedCapabilities, newArtifacts, toolOutputs, attempts);
    }

    public WorkflowStep withFailure(
            String error,
            List<String> newUsedTools,
            List<String> newUsedCapabilities,
            List<String> newArtifacts,
            List<String> newToolOutputs,
            int attempts
    ) {
        return new WorkflowStep(
                agent,
                description,
                requiredTools,
                newUsedTools,
                newUsedCapabilities,
                newArtifacts,
                newToolOutputs,
                parameterContext,
                StepStatus.FAILED,
                null,
                startTime,
                LocalDateTime.now(),
                error,
                attempts,
                false
        );
    }

    public WorkflowStep withRetry() {
        return new WorkflowStep(
                agent,
                description,
                requiredTools,
                usedTools,
                usedCapabilities,
                artifacts,
                toolOutputs,
                parameterContext,
                StepStatus.IN_PROGRESS,
                null,
                startTime,
                null,
                null,
                attemptCount + 1,
                false
        );
    }

    public boolean isCompleted() {
        return status == StepStatus.COMPLETED || status == StepStatus.SKIPPED;
    }

    public boolean needsHumanIntervention() {
        return status.needsHumanIntervention() || needsHumanFeedback;
    }

    @Override
    public String toString() {
        return String.format("WorkflowStep{agent='%s', desc='%s', status=%s, attempts=%d}", agent, description, status, attemptCount);
    }
}
