package com.openmanus.saa.model;

import java.util.Locale;

public enum ResponseMode {
    FINAL_DELIVERABLE,
    WORKFLOW_SUMMARY,
    HYBRID;

    public static ResponseMode from(Object value) {
        if (value instanceof ResponseMode responseMode) {
            return responseMode;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return ResponseMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
