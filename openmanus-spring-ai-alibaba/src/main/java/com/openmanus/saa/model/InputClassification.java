package com.openmanus.saa.model;

import java.util.Map;

/**
 * 用户输入分类结果。
 */
public record InputClassification(
    UserInputIntent intent,
    Map<String, Object> extractedParams,
    String reasoning
) {
    public static InputClassification supplementInfo(Map<String, Object> params) {
        return new InputClassification(UserInputIntent.SUPPLEMENT_INFO, params, null);
    }

    public static InputClassification continueExecution() {
        return new InputClassification(UserInputIntent.CONTINUE, Map.of(), null);
    }

    public static InputClassification newTask() {
        return new InputClassification(UserInputIntent.NEW_TASK, Map.of(), null);
    }

    public static InputClassification uncertain(String reason) {
        return new InputClassification(UserInputIntent.UNCERTAIN, Map.of(), reason);
    }

    public boolean isSupplementInfo() {
        return intent == UserInputIntent.SUPPLEMENT_INFO;
    }

    public boolean isContinue() {
        return intent == UserInputIntent.CONTINUE;
    }

    public boolean isNewTask() {
        return intent == UserInputIntent.NEW_TASK;
    }
}