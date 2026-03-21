package com.openmanus.saa.model;

import jakarta.validation.constraints.NotBlank;

public record WorkflowFeedbackRequest(
        @NotBlank String sessionId,
        HumanFeedbackResponse.ActionType action,
        String userInput,
        String providedInfo,
        String modifiedParams
) {
}
