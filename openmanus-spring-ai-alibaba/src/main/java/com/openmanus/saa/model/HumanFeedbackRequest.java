package com.openmanus.saa.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class HumanFeedbackRequest {

    private final String sessionId;
    private final String objective;
    private final String planId;
    private final int stepIndex;
    private final WorkflowStep failedStep;
    private final List<WorkflowStep> steps;
    private final List<WorkflowStep> completedSteps;
    private final String errorMessage;
    private final String suggestedAction;
    private final LocalDateTime requestTime;
    private final String userFeedback;

    public HumanFeedbackRequest(
            String sessionId,
            String objective,
            String planId,
            int stepIndex,
            WorkflowStep failedStep,
            List<WorkflowStep> steps,
            String errorMessage,
            String suggestedAction
    ) {
        this(sessionId, objective, planId, stepIndex, failedStep, steps, errorMessage, suggestedAction, null, null);
    }

    @JsonCreator
    public HumanFeedbackRequest(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("objective") String objective,
            @JsonProperty("planId") String planId,
            @JsonProperty("stepIndex") int stepIndex,
            @JsonProperty("failedStep") WorkflowStep failedStep,
            @JsonProperty("steps") List<WorkflowStep> steps,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("suggestedAction") String suggestedAction,
            @JsonProperty("userFeedback") String userFeedback,
            @JsonProperty("requestTime") LocalDateTime requestTime
    ) {
        this.sessionId = sessionId;
        this.objective = objective;
        this.planId = planId;
        this.stepIndex = stepIndex;
        this.failedStep = failedStep;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.completedSteps = this.steps.stream()
                .limit(Math.max(0, Math.min(this.stepIndex, this.steps.size())))
                .filter(WorkflowStep::isCompleted)
                .collect(Collectors.toList());
        this.errorMessage = errorMessage;
        this.suggestedAction = suggestedAction;
        this.requestTime = requestTime == null ? LocalDateTime.now() : requestTime;
        this.userFeedback = userFeedback;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getObjective() {
        return objective;
    }

    public String getPlanId() {
        return planId;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public WorkflowStep getFailedStep() {
        return failedStep;
    }

    public List<WorkflowStep> getSteps() {
        return steps;
    }

    public List<WorkflowStep> getCompletedSteps() {
        return completedSteps;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public String getUserFeedback() {
        return userFeedback;
    }

    public String getUserMessage() {
        boolean chinese = ResponseLanguageHelper.detect(objective) == ResponseLanguageHelper.Language.ZH_CN;
        StringBuilder sb = new StringBuilder();
        sb.append(chinese ? "\u9700\u8981\u7528\u6237\u53cd\u9988\u3002\n\n" : "Need human feedback.\n\n");
        sb.append(chinese ? "\u76ee\u6807: " : "Objective: ").append(objective).append("\n");
        sb.append(chinese ? "\u6b65\u9aa4: " : "Step: ").append(stepIndex + 1).append("\n");
        sb.append(chinese ? "\u4efb\u52a1: " : "Task: ").append(failedStep.getDescription()).append("\n");
        sb.append(chinese ? "\u95ee\u9898: " : "Issue: ").append(errorMessage).append("\n");
        if (suggestedAction != null && !suggestedAction.isBlank()) {
            sb.append(chinese ? "\u5efa\u8bae\u64cd\u4f5c: " : "Suggested action: ")
                    .append(suggestedAction)
                    .append("\n");
        }
        sb.append(chinese
                ? "\u53ef\u76f4\u63a5\u56de\u7b54\uff1a\u201c\u7528\u4fee\u6b63\u540e\u7684\u53c2\u6570\u518d\u8bd5\u201d\u3001\u201c\u8df3\u8fc7\u8fd9\u4e00\u6b65\u201d\u3001\u201c\u7ed3\u675f\u6d41\u7a0b\u201d\u3002\n"
                : "You can reply directly with phrases like \"retry with corrected parameters\", \"skip this step\", or \"end the workflow\".\n");
        return sb.toString();
    }
}
