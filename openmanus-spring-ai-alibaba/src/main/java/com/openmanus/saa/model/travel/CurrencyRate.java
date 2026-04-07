package com.openmanus.saa.model.travel;

import java.time.LocalDate;

public record CurrencyRate(
    String base,
    String target,
    double rate,
    double amount,
    double converted,
    LocalDate date
) {}
