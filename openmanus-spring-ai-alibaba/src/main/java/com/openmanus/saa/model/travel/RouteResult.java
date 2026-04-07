package com.openmanus.saa.model.travel;

import java.util.List;

public record RouteResult(
    double distance,
    String distanceUnit,
    double duration,
    String durationUnit,
    String mode,
    List<RouteStep> steps
) {}
