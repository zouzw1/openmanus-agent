package com.openmanus.saa.model;

import java.util.List;

public record WorkflowExecutionResponse(
        String objective,
        List<WorkflowStep> steps,
        List<String> executionLog,
        String summary
) {
}
