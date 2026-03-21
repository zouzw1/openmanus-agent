package com.openmanus.saa.model;

/**
 * 人工反馈响应模型
 */
public class HumanFeedbackResponse {
    
    public enum ActionType {
        /**
         * 提供补充信息，继续执行
         */
        PROVIDE_INFO,
        
        /**
         * 跳过当前步骤
         */
        SKIP_STEP,
        
        /**
         * 重试当前步骤
         */
        RETRY,
        
        /**
         * 终止整个计划
         */
        ABORT_PLAN,
        
        /**
         * 修改参数后重试
         */
        MODIFY_AND_RETRY
    }
    
    private final ActionType action;
    private final String providedInfo;      // 当 action=PROVIDE_INFO 时的补充信息
    private final String modifiedParams;    // 当 action=MODIFY_AND_RETRY 时的修改参数
    
    public HumanFeedbackResponse(ActionType action, String providedInfo, String modifiedParams) {
        this.action = action;
        this.providedInfo = providedInfo;
        this.modifiedParams = modifiedParams;
    }
    
    // 静态工厂方法
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
    
    // Getters
    public ActionType getAction() { return action; }
    public String getProvidedInfo() { return providedInfo; }
    public String getModifiedParams() { return modifiedParams; }
    
    @Override
    public String toString() {
        return String.format("HumanFeedbackResponse{action=%s, info='%s', params='%s'}",
            action, providedInfo != null ? providedInfo : "N/A", 
            modifiedParams != null ? modifiedParams : "N/A");
    }
}
