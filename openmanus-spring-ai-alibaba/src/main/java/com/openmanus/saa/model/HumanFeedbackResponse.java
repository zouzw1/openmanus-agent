package com.openmanus.saa.model;

public class HumanFeedbackResponse {

    public enum ActionType {
        PROVIDE_INFO,
        SKIP_STEP,
        RETRY,
        ABORT_PLAN,
        MODIFY_AND_RETRY
    }

    private final ActionType action;
    private final String providedInfo;
    private final String modifiedParams;
    private final InferencePolicy inferencePolicy;
    private final boolean replanRequired;
    private final String updatedObjective;

    public HumanFeedbackResponse(ActionType action, String providedInfo, String modifiedParams) {
        this(action, providedInfo, modifiedParams, null, false, null);
    }

    public HumanFeedbackResponse(
            ActionType action,
            String providedInfo,
            String modifiedParams,
            InferencePolicy inferencePolicy
    ) {
        this(action, providedInfo, modifiedParams, inferencePolicy, false, null);
    }

    public HumanFeedbackResponse(
            ActionType action,
            String providedInfo,
            String modifiedParams,
            InferencePolicy inferencePolicy,
            boolean replanRequired,
            String updatedObjective
    ) {
        this.action = action;
        this.providedInfo = providedInfo;
        this.modifiedParams = modifiedParams;
        this.inferencePolicy = inferencePolicy;
        this.replanRequired = replanRequired;
        this.updatedObjective = updatedObjective;
    }

    public static HumanFeedbackResponse provideInfo(String info) {
        return new HumanFeedbackResponse(ActionType.PROVIDE_INFO, info, null);
    }

    public static HumanFeedbackResponse skipStep() {
        return new HumanFeedbackResponse(ActionType.SKIP_STEP, null, null);
    }

    public static HumanFeedbackResponse retry() {
        return new HumanFeedbackResponse(ActionType.RETRY, null, null);
    }

    public static HumanFeedbackResponse abortPlan() {
        return new HumanFeedbackResponse(ActionType.ABORT_PLAN, null, null);
    }

    public static HumanFeedbackResponse modifyAndRetry(String params) {
        return new HumanFeedbackResponse(ActionType.MODIFY_AND_RETRY, null, params);
    }

    public ActionType getAction() {
        return action;
    }

    public String getProvidedInfo() {
        return providedInfo;
    }

    public String getModifiedParams() {
        return modifiedParams;
    }

    public InferencePolicy getInferencePolicy() {
        return inferencePolicy;
    }

    public boolean isReplanRequired() {
        return replanRequired;
    }

    public String getUpdatedObjective() {
        return updatedObjective;
    }

    @Override
    public String toString() {
        return String.format(
                "HumanFeedbackResponse{action=%s, info='%s', params='%s', inferencePolicy='%s', replanRequired=%s, updatedObjective='%s'}",
                action,
                providedInfo != null ? providedInfo : "N/A",
                modifiedParams != null ? modifiedParams : "N/A",
                inferencePolicy != null ? inferencePolicy : "N/A",
                replanRequired,
                updatedObjective != null ? updatedObjective : "N/A"
        );
    }
}
