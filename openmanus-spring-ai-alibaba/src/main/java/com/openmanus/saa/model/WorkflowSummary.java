package com.openmanus.saa.model;

public record WorkflowSummary(
        WorkflowExecutionStatus status,
        String statusLabel,
        int totalSteps,
        int completedSteps,
        int skippedSteps,
        int failedSteps,
        boolean requiresHumanFeedback,
        String currentStep,
        String userMessage
) {
}
