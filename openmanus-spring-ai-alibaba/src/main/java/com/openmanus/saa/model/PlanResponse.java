package com.openmanus.saa.model;

import java.util.List;

public record PlanResponse(
        String objective,
        List<String> steps,
        String summary
) {
    public PlanResponse(String objective, List<String> steps) {
        this(objective, steps, null);
    }
}
