package com.openmanus.saa.model.travel;

import java.time.LocalDate;
import java.util.List;

public record HolidayInfo(
    LocalDate date,
    String localName,
    String name,
    String countryCode,
    boolean fixed,
    List<String> types
) {}
