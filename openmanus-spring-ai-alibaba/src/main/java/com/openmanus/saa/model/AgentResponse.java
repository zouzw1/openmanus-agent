package com.openmanus.saa.model;

import java.util.List;

public record AgentResponse(
        String mode,
        String objective,
        String summary,
        String content,
        List<String> steps,
        List<WorkflowStep> workflowSteps,
        List<String> executionLog,
        WorkflowSummary workflowSummary,
        HumanFeedbackRequest pendingFeedback
) {
    public AgentResponse(String mode, String content, List<String> steps) {
        this(mode, null, null, content, steps, List.of(), List.of(), null, null);
    }

    public AgentResponse(
            String mode,
            String objective,
            String summary,
            String content,
            List<String> steps
    ) {
        this(mode, objective, summary, content, steps, List.of(), List.of(), null, null);
    }
}
