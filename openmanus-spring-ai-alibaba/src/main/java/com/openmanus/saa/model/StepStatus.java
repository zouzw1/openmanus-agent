package com.openmanus.saa.model;

public enum StepStatus {
    NOT_STARTED("未开始"),
    IN_PROGRESS("执行中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    FAILED_NEEDS_HUMAN_INTERVENTION("失败 - 需要人工介入"),
    WAITING_USER_CLARIFICATION("等待用户澄清"),
    SKIPPED("已跳过");

    private final String chineseName;

    StepStatus(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == FAILED;
    }

    public boolean needsHumanIntervention() {
        return this == FAILED_NEEDS_HUMAN_INTERVENTION || this == WAITING_USER_CLARIFICATION;
    }
}
