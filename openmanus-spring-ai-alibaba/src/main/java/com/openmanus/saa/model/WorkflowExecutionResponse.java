package com.openmanus.saa.model;

import java.util.List;

public record WorkflowExecutionResponse(
        String objective,
        List<WorkflowStep> steps,
        List<String> executionLog,
        WorkflowSummary summary,
        HumanFeedbackRequest pendingFeedback
) {
    public WorkflowExecutionResponse(
            String objective,
            List<WorkflowStep> steps,
            List<String> executionLog,
            WorkflowSummary summary
    ) {
        this(objective, steps, executionLog, summary, null);
    }
}
