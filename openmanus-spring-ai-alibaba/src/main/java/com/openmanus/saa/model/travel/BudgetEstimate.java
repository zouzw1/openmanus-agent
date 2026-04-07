package com.openmanus.saa.model.travel;

import java.util.Map;

public record BudgetEstimate(
    String destination,
    int duration,
    String travelStyle,
    int groupSize,
    Map<String, Double> breakdown,
    double total,
    String currency
) {}
