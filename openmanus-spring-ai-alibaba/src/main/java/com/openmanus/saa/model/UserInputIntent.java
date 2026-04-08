package com.openmanus.saa.model;

/**
 * 用户输入意图类型。
 * 用于区分用户输入是对暂停工作流的反馈还是新任务。
 */
public enum UserInputIntent {
    /** 补充信息：用户回答之前的问题 */
    SUPPLEMENT_INFO,
    /** 继续执行：用户明确要求继续 */
    CONTINUE,
    /** 新任务：完全独立的请求 */
    NEW_TASK,
    /** 不确定：需要进一步确认 */
    UNCERTAIN
}