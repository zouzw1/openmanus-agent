package com.openmanus.saa.model.travel;

public record RouteStep(
    String instruction,
    double distance,
    double duration
) {}
