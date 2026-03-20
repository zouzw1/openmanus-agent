package com.openmanus.saa.model.browser;

import jakarta.validation.constraints.NotBlank;

public record BrowserActionRequest(
        @NotBlank String action,
        String url,
        String selector,
        String text,
        Integer scrollAmount
) {
}
