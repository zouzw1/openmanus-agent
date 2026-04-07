package com.openmanus.saa.model.travel;

import java.time.LocalDate;

public record ForecastInfo(
    LocalDate date,
    int day,
    int temperatureMax,
    int temperatureMin,
    String condition,
    int precipitationChance,
    int weatherCode
) {}
