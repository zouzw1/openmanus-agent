package com.openmanus.saa.model.travel;

import java.util.List;

public record POIItem(
    String name,
    GeoLocation location,
    String address,
    String type,
    List<String> tags,
    String openingHours
) {}
