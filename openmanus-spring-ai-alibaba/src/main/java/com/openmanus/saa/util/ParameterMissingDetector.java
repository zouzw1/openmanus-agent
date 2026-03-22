package com.openmanus.saa.util;

import java.util.regex.Pattern;

public class ParameterMissingDetector {

    private static final Pattern[] MISSING_PARAM_PATTERNS = new Pattern[]{
            Pattern.compile("missing.*param", Pattern.CASE_INSENSITIVE),
            Pattern.compile("required.*param", Pattern.CASE_INSENSITIVE),
            Pattern.compile("missing.*argument", Pattern.CASE_INSENSITIVE),
            Pattern.compile("required.*argument", Pattern.CASE_INSENSITIVE),
            Pattern.compile("not found", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cannot be null", Pattern.CASE_INSENSITIVE),
            Pattern.compile("must be provided", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expects.*but got", Pattern.CASE_INSENSITIVE),
            Pattern.compile("invalid.*param", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\u7f3a\\u5c11\\u53c2\\u6570"),
            Pattern.compile("\\u5fc5\\u586b\\u53c2\\u6570"),
            Pattern.compile("\\u53c2\\u6570.*\\u7f3a\\u5931")
    };

    private static final Pattern[] USER_CLARIFICATION_PATTERNS = new Pattern[]{
            Pattern.compile("ask.*user", Pattern.CASE_INSENSITIVE),
            Pattern.compile("please provide", Pattern.CASE_INSENSITIVE),
            Pattern.compile("which (city|location|time)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("need to know", Pattern.CASE_INSENSITIVE),
            Pattern.compile("clarification", Pattern.CASE_INSENSITIVE),
            Pattern.compile("unable to.*without", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\u8bf7\\u63d0\\u4f9b"),
            Pattern.compile("\\u8bf7\\u8865\\u5145"),
            Pattern.compile("\\u8bf7\\u786e\\u8ba4"),
            Pattern.compile("\\u9700\\u8981\\u4f60\\u63d0\\u4f9b"),
            Pattern.compile("\\u8bf7\\u95ee")
    };

    private static final Pattern[] FAILURE_PATTERNS = new Pattern[]{
            Pattern.compile("error", Pattern.CASE_INSENSITIVE),
            Pattern.compile("failed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("unable to", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cannot", Pattern.CASE_INSENSITIVE),
            Pattern.compile("could not", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\u65e0\\u6cd5"),
            Pattern.compile("\\u5931\\u8d25"),
            Pattern.compile("\\u9519\\u8bef"),
            Pattern.compile("\\u672a\\u80fd"),
            Pattern.compile("\\u4e0d\\u652f\\u6301")
    };

    private static final Pattern[] SUCCESS_PATTERNS = new Pattern[]{
            Pattern.compile("\"success\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE),
            Pattern.compile("successfully", Pattern.CASE_INSENSITIVE),
            Pattern.compile("completed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("generated", Pattern.CASE_INSENSITIVE),
            Pattern.compile("created", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exported", Pattern.CASE_INSENSITIVE),
            Pattern.compile("plan created", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wrote file", Pattern.CASE_INSENSITIVE),
            Pattern.compile("saved to", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bok\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\u5df2\\u6210\\u529f"),
            Pattern.compile("\\u5df2\\u751f\\u6210"),
            Pattern.compile("\\u5df2\\u521b\\u5efa"),
            Pattern.compile("\\u5df2\\u5199\\u5165"),
            Pattern.compile("\\u5df2\\u5bfc\\u51fa"),
            Pattern.compile("\\u6210\\u529f\\u83b7\\u53d6"),
            Pattern.compile("\\u6210\\u529f\\u67e5\\u8be2"),
            Pattern.compile("\\u5df2\\u83b7\\u53d6"),
            Pattern.compile("\\u5df2\\u5b8c\\u6210"),
            Pattern.compile("\\u5df2\\u4fdd\\u5b58"),
            Pattern.compile("\\u67e5\\u8be2\\u7ed3\\u679c"),
            Pattern.compile("\\u4e0b\\u4e00\\u6b65\\u5c06")
    };

    public enum DetectionResult {
        SUCCESS,
        MISSING_PARAMETERS,
        NEEDS_USER_CLARIFICATION,
        OTHER_ERROR
    }

    public static DetectionResult detect(String result) {
        if (result == null || result.isEmpty()) {
            return DetectionResult.OTHER_ERROR;
        }

        if (needsUserClarification(result)) {
            return DetectionResult.NEEDS_USER_CLARIFICATION;
        }
        if (hasMissingParameters(result)) {
            return DetectionResult.MISSING_PARAMETERS;
        }
        if (isSuccess(result)) {
            return DetectionResult.SUCCESS;
        }
        return DetectionResult.OTHER_ERROR;
    }

    public static boolean isSuccess(String result) {
        if (result == null || result.isEmpty() || hasExplicitFailure(result)) {
            return false;
        }

        for (Pattern pattern : SUCCESS_PATTERNS) {
            if (pattern.matcher(result).find()) {
                return true;
            }
        }

        String lowerResult = result.toLowerCase();
        return lowerResult.contains("temperature")
                || lowerResult.contains("humidity")
                || result.contains("\u6e29\u5ea6")
                || result.contains("\u6e7f\u5ea6")
                || result.contains("\u98ce\u901f");
    }

    public static boolean hasMissingParameters(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }

        for (Pattern pattern : MISSING_PARAM_PATTERNS) {
            if (pattern.matcher(result).find()) {
                return true;
            }
        }

        return false;
    }

    public static boolean needsUserClarification(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }

        if (result.contains("TOOL_CALL_ERROR")
                || result.contains("ACTION_REQUIRED")
                || result.contains("NEEDS_CLARIFICATION")) {
            return true;
        }

        for (Pattern pattern : USER_CLARIFICATION_PATTERNS) {
            if (pattern.matcher(result).find()) {
                return true;
            }
        }

        return false;
    }

    public static String extractMissingParameter(String result) {
        if (result == null || result.isEmpty()) {
            return null;
        }

        Pattern quotePattern = Pattern.compile("'([^']+)'");
        var matcher = quotePattern.matcher(result);
        if (matcher.find() && matcher.groupCount() > 0) {
            return matcher.group(1);
        }

        Pattern paramPattern = Pattern.compile("parameter\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
        matcher = paramPattern.matcher(result);
        if (matcher.find() && matcher.groupCount() > 0) {
            return matcher.group(1);
        }

        return null;
    }

    private static boolean hasExplicitFailure(String result) {
        for (Pattern pattern : FAILURE_PATTERNS) {
            if (pattern.matcher(result).find()) {
                return true;
            }
        }
        return false;
    }

    public static String generateRetryGuidance() {
        return """

                UNIVERSAL PARAMETER HANDLING WORKFLOW (WITHIN CURRENT STEP):
                When any tool/skill call fails during this step:

                DETECTION PHASE:
                - Look for error indicators: "missing", "required", "not found", "null", etc.
                - Identify what information is missing

                RETRY PHASE (MUST HAPPEN IN CURRENT STEP):
                1. FIRST: Search conversation history and context for the missing information
                   - Check user's previous messages
                   - Check earlier results from other tools
                   - Check any mentioned values (cities, times, names, etc.)

                2. IF FOUND: Use that value to fill in the parameter and RETRY IMMEDIATELY
                   - Construct the corrected tool/skill call
                   - Execute the retry within this same step
                   - If retry succeeds, continue with remaining actions in THIS STEP

                3. IF NOT FOUND: End this step with a clear question for the user
                   - Ask specifically for the missing parameter
                   - Do NOT assume default values
                   - The workflow engine will get user clarification before next step

                4. NEVER:
                   - Assume default values or make up data
                   - Defer retries to the next step
                   - Proceed without required parameters
                """;
    }
}
