package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 会话压缩配置。
 * 控制压缩行为的关键参数。
 */
public record CompactionConfig(
    /**
     * 保留最近N条消息（不会被压缩）
     */
    int preserveRecentMessages,

    /**
     * 触发压缩的Token估算阈值
     */
    int maxEstimatedTokens
) {
    /**
     * 默认配置实例
     */
    public static final CompactionConfig DEFAULT = new CompactionConfig(4, 10000);

    @JsonCreator
    public CompactionConfig(
        @JsonProperty("preserveRecentMessages") int preserveRecentMessages,
        @JsonProperty("maxEstimatedTokens") int maxEstimatedTokens
    ) {
        this.preserveRecentMessages = preserveRecentMessages;
        this.maxEstimatedTokens = maxEstimatedTokens;
    }

    /**
     * 获取默认配置
     */
    public static CompactionConfig defaultConfig() {
        return DEFAULT;
    }
}