package com.openmanus.saa.model.travel;

public final class TravelConstants {
    private TravelConstants() {}

    public static final int DEFAULT_RADIUS = 5000;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_FORECAST_DAYS = 7;

    public enum TransportMode {
        DRIVING, WALKING, CYCLING;

        public String toOsrmString() {
            return name().toLowerCase();
        }
    }

    public enum TravelStyle {
        BUDGET, MID_RANGE, LUXURY
    }
}
