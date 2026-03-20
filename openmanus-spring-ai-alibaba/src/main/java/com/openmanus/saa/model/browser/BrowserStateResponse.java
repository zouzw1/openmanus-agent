package com.openmanus.saa.model.browser;

public record BrowserStateResponse(
        boolean enabled,
        String url,
        String title,
        String contentPreview
) {
}
