package com.openmanus.saa.model.travel;

public record GeoLocation(double lat, double lon) {
    public String toCoordinateString() {
        return lat + "," + lon;
    }

    public static GeoLocation fromCoordinateString(String coord) {
        String[] parts = coord.split(",");
        return new GeoLocation(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
    }
}
