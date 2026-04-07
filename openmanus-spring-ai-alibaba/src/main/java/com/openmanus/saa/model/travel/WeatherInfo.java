package com.openmanus.saa.model.travel;

public record WeatherInfo(
    int temperature,
    String condition,
    int humidity,
    int windSpeed,
    boolean isDay,
    int weatherCode
) {}
