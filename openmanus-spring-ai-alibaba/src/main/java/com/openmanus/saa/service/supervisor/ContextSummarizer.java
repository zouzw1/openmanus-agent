package com.openmanus.saa.service.supervisor;

import java.util.List;

/**
 * 上下文摘要器接口。
 * 负责将长上下文压缩为简洁的摘要，类似于Claude Code的上下文管理。
 */
public interface ContextSummarizer {

    /**
     * 将长上下文压缩为指定长度的摘要。
     *
     * @param content 原始内容
     * @param maxLength 最大长度
     * @return 摘要后的内容
     */
    String summarize(String content, int maxLength);

    /**
     * 从内容中提取关键信息点。
     *
     * @param content 原始内容
     * @return 关键信息列表
     */
    List<String> extractKeyPoints(String content);

    /**
     * 创建包含摘要历史的压缩消息。
     *
     * @param originalContent 原始内容
     * @param summary 摘要
     * @return 格式化后的消息
     */
    default String formatSummarizedMessage(String originalContent, String summary) {
        return String.format("[前 %d 字符已摘要]\n\n%s", originalContent.length(), summary);
    }

    /**
     * 检查内容是否需要摘要。
     *
     * @param content 内容
     * @param threshold 阈值
     * @return 是否需要摘要
     */
    default boolean needsSummarization(String content, int threshold) {
        return content != null && content.length() > threshold;
    }
}
