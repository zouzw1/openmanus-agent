package com.openmanus.saa.model;

/**
 * 工作流步骤执行状态
 */
public enum StepStatus {
    /**
     * 未开始
     */
    NOT_STARTED("未开始"),
    
    /**
     * 执行中
     */
    IN_PROGRESS("执行中"),
    
    /**
     * 已完成
     */
    COMPLETED("已完成"),
    
    /**
     * 失败，需要人工介入
     */
    FAILED_NEEDS_HUMAN_INTERVENTION("失败 - 需要人工介入"),
    
    /**
     * 等待用户澄清
     */
    WAITING_USER_CLARIFICATION("等待用户澄清"),
    
    /**
     * 已跳过
     */
    SKIPPED("已跳过");
    
    private final String chineseName;
    
    StepStatus(String chineseName) {
        this.chineseName = chineseName;
    }
    
    public String getChineseName() {
        return chineseName;
    }
    
    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED;
    }
    
    /**
     * 判断是否需要人工介入
     */
    public boolean needsHumanIntervention() {
        return this == FAILED_NEEDS_HUMAN_INTERVENTION || this == WAITING_USER_CLARIFICATION;
    }
}
