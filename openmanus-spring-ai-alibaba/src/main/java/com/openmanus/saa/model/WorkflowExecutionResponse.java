package com.openmanus.saa.model;

import java.util.List;

public record WorkflowExecutionResponse(
        String objective,
        List<WorkflowStep> steps,
        List<String> artifacts,
        List<String> executionLog,
        WorkflowSummary summary,
        HumanFeedbackRequest pendingFeedback
) {
    public WorkflowExecutionResponse(
            String objective,
            List<WorkflowStep> steps,
            List<String> artifacts,
            List<String> executionLog,
            WorkflowSummary summary
    ) {
        this(objective, steps, artifacts, executionLog, summary, null);
    }

    public WorkflowExecutionResponse(
            String objective,
            List<WorkflowStep> steps,
            List<String> executionLog,
            WorkflowSummary summary
    ) {
        this(objective, steps, List.of(), executionLog, summary, null);
    }
}
