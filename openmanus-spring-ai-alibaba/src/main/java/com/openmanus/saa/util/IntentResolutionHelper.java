package com.openmanus.saa.util;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.ResponseMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntentResolutionHelper {

    public static final String RESPONSE_MODE_ATTRIBUTE = "responseMode";
    public static final String NEEDS_FILE_ATTRIBUTE = "needsFile";
    public static final String USER_FORMAT_ATTRIBUTE = "userFormat";
    public static final String REASON_ATTRIBUTE = "reason";

    private IntentResolutionHelper() {
    }

    public static ResponseMode responseMode(IntentResolution resolution) {
        if (resolution == null || resolution.attributes() == null) {
            return null;
        }
        return ResponseMode.from(resolution.attributes().get(RESPONSE_MODE_ATTRIBUTE));
    }

    public static IntentResolution withResponseMode(IntentResolution resolution, ResponseMode responseMode) {
        if (resolution == null || responseMode == null) {
            return resolution;
        }
        ResponseMode existing = responseMode(resolution);
        if (existing == responseMode) {
            return resolution;
        }
        Map<String, Object> attributes = new LinkedHashMap<>(resolution.attributes());
        attributes.put(RESPONSE_MODE_ATTRIBUTE, responseMode.name());
        return new IntentResolution(
                resolution.intentId(),
                resolution.confidence(),
                resolution.routeMode(),
                resolution.preferredAgentId(),
                attributes,
                resolution.planningHints()
        );
    }

    public static boolean needsFile(IntentResolution resolution) {
        if (resolution == null || resolution.attributes() == null) {
            return false;
        }
        Object value = resolution.attributes().get(NEEDS_FILE_ATTRIBUTE);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.TRUE.equals(String.valueOf(value));
    }

    public static String getUserSpecifiedFormat(IntentResolution resolution) {
        if (resolution == null || resolution.attributes() == null) {
            return null;
        }
        Object value = resolution.attributes().get(USER_FORMAT_ATTRIBUTE);
        return value != null ? String.valueOf(value) : null;
    }

    public static String getReason(IntentResolution resolution) {
        if (resolution == null || resolution.attributes() == null) {
            return null;
        }
        Object value = resolution.attributes().get(REASON_ATTRIBUTE);
        return value != null ? String.valueOf(value) : null;
    }
}
