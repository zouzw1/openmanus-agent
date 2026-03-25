package com.openmanus.saa.service.summary;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.WorkflowExecutionStatus;
import com.openmanus.saa.model.WorkflowStep;
import java.util.List;

public record WorkflowSummaryContext(
        String objective,
        WorkflowExecutionStatus status,
        String currentStep,
        String baseMessage,
        WorkflowStep failedStep,
        HumanFeedbackRequest pendingFeedback,
        List<WorkflowStep> executedSteps,
        List<String> artifacts,
        List<String> executionLog
) {
    public WorkflowSummaryContext {
        executedSteps = executedSteps == null ? List.of() : List.copyOf(executedSteps);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        executionLog = executionLog == null ? List.of() : List.copyOf(executionLog);
    }
}
