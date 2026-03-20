package com.openmanus.saa.model;

import java.util.List;

public record PlanResponse(
        String objective,
        List<String> steps
) {
}
