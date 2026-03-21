package com.openmanus.saa.util;

import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
import java.util.Locale;

public final class HumanFeedbackResolver {

    private HumanFeedbackResolver() {
    }

    public static HumanFeedbackResponse resolve(WorkflowFeedbackRequest request) {
        if (request.action() != null) {
            return buildResponse(request.action(), extractRawInput(request), request);
        }

        String rawInput = extractRawInput(request);
        if (rawInput == null) {
            throw new IllegalArgumentException("Either action or userInput must be provided");
        }

        return buildResponse(inferAction(rawInput), rawInput, request);
    }

    public static String extractRawInput(WorkflowFeedbackRequest request) {
        return firstNonBlank(request.userInput(), request.modifiedParams(), request.providedInfo());
    }

    public static HumanFeedbackResponse buildResponse(
            HumanFeedbackResponse.ActionType action,
            String rawInput,
            WorkflowFeedbackRequest request
    ) {
        return switch (action) {
            case ABORT_PLAN -> HumanFeedbackResponse.abortPlan();
            case SKIP_STEP -> HumanFeedbackResponse.skipStep();
            case RETRY -> HumanFeedbackResponse.retry();
            case MODIFY_AND_RETRY -> new HumanFeedbackResponse(
                    action,
                    null,
                    firstNonBlank(request.modifiedParams(), rawInput, request.providedInfo())
            );
            case PROVIDE_INFO -> new HumanFeedbackResponse(
                    action,
                    firstNonBlank(request.providedInfo(), rawInput, request.modifiedParams()),
                    null
            );
        };
    }

    public static boolean isStrongRuleAction(HumanFeedbackResponse.ActionType action) {
        return action != null && action != HumanFeedbackResponse.ActionType.PROVIDE_INFO;
    }

    public static HumanFeedbackResponse.ActionType inferAction(String input) {
        String normalized = normalize(input);

        if (containsAny(
                normalized,
                "\u7ec8\u6b62",
                "\u7ed3\u675f\u6d41\u7a0b",
                "\u7ed3\u675f\u4efb\u52a1",
                "\u505c\u6b62\u6d41\u7a0b",
                "\u505c\u6b62\u4efb\u52a1",
                "\u53d6\u6d88\u4efb\u52a1",
                "abort",
                "stop workflow",
                "end workflow",
                "cancel workflow")) {
            return HumanFeedbackResponse.ActionType.ABORT_PLAN;
        }
        if (containsAny(
                normalized,
                "\u8df3\u8fc7",
                "\u5ffd\u7565\u8fd9\u6b65",
                "\u5ffd\u7565\u8be5\u6b65",
                "skip step",
                "skip this step",
                "continue without")) {
            return HumanFeedbackResponse.ActionType.SKIP_STEP;
        }
        if (containsAny(
                normalized,
                "\u6539\u6210",
                "\u6539\u4e3a",
                "\u6362\u6210",
                "\u6539\u7528",
                "\u7528beijing",
                "\u7528 beijing",
                "retry with",
                "try with",
                "change to",
                "replace with",
                "use beijing")) {
            return HumanFeedbackResponse.ActionType.MODIFY_AND_RETRY;
        }
        if (containsAny(
                normalized,
                "\u91cd\u8bd5",
                "\u518d\u8bd5\u4e00\u6b21",
                "\u91cd\u65b0\u6267\u884c",
                "\u91cd\u65b0\u67e5\u8be2",
                "retry",
                "try again",
                "run again")) {
            return HumanFeedbackResponse.ActionType.RETRY;
        }
        return HumanFeedbackResponse.ActionType.PROVIDE_INFO;
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String input, String... keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
