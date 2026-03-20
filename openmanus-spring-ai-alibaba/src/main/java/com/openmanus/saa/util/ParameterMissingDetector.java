package com.openmanus.saa.util;

import java.util.regex.Pattern;

/**
 * 通用参数缺失检测工具类
 * 用于识别各种工具调用失败是否由于缺少参数导致
 */
public class ParameterMissingDetector {

    // 常见的参数缺失错误模式
    private static final Pattern[] MISSING_PARAM_PATTERNS = new Pattern[]{
            Pattern.compile("missing.*param", Pattern.CASE_INSENSITIVE),
            Pattern.compile("required.*param", Pattern.CASE_INSENSITIVE),
            Pattern.compile("missing.*argument", Pattern.CASE_INSENSITIVE),
            Pattern.compile("required.*argument", Pattern.CASE_INSENSITIVE),
            Pattern.compile("not found", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cannot be null", Pattern.CASE_INSENSITIVE),
            Pattern.compile("must be provided", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expects.*but got", Pattern.CASE_INSENSITIVE),
            Pattern.compile("invalid.*param", Pattern.CASE_INSENSITIVE)
    };

    // 常见的询问用户模式
    private static final Pattern[] USER_CLARIFICATION_PATTERNS = new Pattern[]{
            Pattern.compile("ask.*user", Pattern.CASE_INSENSITIVE),
            Pattern.compile("please provide", Pattern.CASE_INSENSITIVE),
            Pattern.compile("which (city|location|time)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("need to know", Pattern.CASE_INSENSITIVE),
            Pattern.compile("clarification", Pattern.CASE_INSENSITIVE),
            Pattern.compile("unable to.*without", Pattern.CASE_INSENSITIVE)
    };

    /**
     * 检测结果枚举
     */
    public enum DetectionResult {
        SUCCESS,                    // 执行成功
        MISSING_PARAMETERS,         // 缺少参数
        NEEDS_USER_CLARIFICATION,   // 需要用户澄清
        OTHER_ERROR                 // 其他错误
    }

    /**
     * 检测执行结果是否表示参数缺失
     *
     * @param result 工具/技能的执行结果
     * @return 检测结果
     */
    public static DetectionResult detect(String result) {
        if (result == null || result.isEmpty()) {
            return DetectionResult.OTHER_ERROR;
        }

        // 检查是否成功
        if (isSuccess(result)) {
            return DetectionResult.SUCCESS;
        }

        // 检查是否需要用户澄清
        if (needsUserClarification(result)) {
            return DetectionResult.NEEDS_USER_CLARIFICATION;
        }

        // 检查是否缺少参数
        if (hasMissingParameters(result)) {
            return DetectionResult.MISSING_PARAMETERS;
        }

        return DetectionResult.OTHER_ERROR;
    }

    /**
     * 判断结果是否表示成功
     */
    public static boolean isSuccess(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }

        // 检查 MCP 工具的成功标记
        if (result.contains("\"success\":true") || result.contains("\"success\": true")) {
            return true;
        }

        // 检查常见的成功标志
        String lowerResult = result.toLowerCase();
        return lowerResult.contains("successfully") ||
               lowerResult.contains("completed") ||
               (lowerResult.contains("ok") && !lowerResult.contains("error"));
    }

    /**
     * 判断结果是否缺少必需参数
     */
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

    /**
     * 判断结果是否需要用户澄清
     */
    public static boolean needsUserClarification(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }

        // 检查明确的澄清标记
        if (result.contains("TOOL_CALL_ERROR") ||
            result.contains("ACTION_REQUIRED") ||
            result.contains("NEEDS_CLARIFICATION")) {
            return true;
        }

        // 检查询问用户的模式
        for (Pattern pattern : USER_CLARIFICATION_PATTERNS) {
            if (pattern.matcher(result).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 从错误消息中提取缺失的参数名（如果可能）
     */
    public static String extractMissingParameter(String result) {
        if (result == null || result.isEmpty()) {
            return null;
        }

        // 尝试提取引号中的参数名
        Pattern quotePattern = Pattern.compile("'([^']+)'");
        var matcher = quotePattern.matcher(result);
        if (matcher.find() && matcher.groupCount() > 0) {
            return matcher.group(1);
        }

        // 尝试提取 parameter 'xxx'
        Pattern paramPattern = Pattern.compile("parameter\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
        matcher = paramPattern.matcher(result);
        if (matcher.find() && matcher.groupCount() > 0) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 生成通用的重试指导说明
     */
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
                
                EXAMPLE PATTERNS TO RECOGNIZE:
                - "Missing required parameter 'city'" → Look for city name in history
                - "Argument cannot be null" → Check if value was provided earlier
                - "Which city are you interested in?" → User needs to clarify
                - "Unable to proceed without location" → Missing location parameter
                
                REMEMBER: All detection, retries, and corrections must happen WITHIN THE CURRENT STEP!
                """;
    }
}
