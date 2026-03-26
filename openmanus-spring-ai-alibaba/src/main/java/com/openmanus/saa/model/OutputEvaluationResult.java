package com.openmanus.saa.model;

import java.util.List;

public record OutputEvaluationResult(
        OutputEvaluationStatus status,
        String message,
        List<String> issues,
        String revisionPrompt,
        int retryCount,
        int maxRetryCount
) {
    public OutputEvaluationResult {
        status = status == null ? OutputEvaluationStatus.SKIPPED : status;
        message = message == null ? "" : message.trim();
        issues = issues == null ? List.of() : List.copyOf(issues);
        revisionPrompt = revisionPrompt == null || revisionPrompt.isBlank() ? null : revisionPrompt.trim();
        retryCount = Math.max(0, retryCount);
        maxRetryCount = Math.max(0, maxRetryCount);
    }
}
