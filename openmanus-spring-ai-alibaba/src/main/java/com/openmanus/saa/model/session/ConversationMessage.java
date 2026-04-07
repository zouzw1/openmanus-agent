package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 会话消息记录。
 * 支持结构化内容块（blocks）和Token使用统计。
 */
public record ConversationMessage(
    MessageRole role,
    List<ContentBlock> blocks,
    TokenUsage usage,
    Instant timestamp
) {

    @JsonCreator
    public ConversationMessage(
        @JsonProperty("role") MessageRole role,
        @JsonProperty("blocks") List<ContentBlock> blocks,
        @JsonProperty("usage") TokenUsage usage,
        @JsonProperty("timestamp") Instant timestamp
    ) {
        this.role = role;
        this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
        this.usage = usage;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * 创建用户文本消息
     */
    public static ConversationMessage userText(String text) {
        return new ConversationMessage(
            MessageRole.USER,
            List.of(new TextBlock(text)),
            null,
            Instant.now()
        );
    }

    /**
     * 创建助手消息（带结构化内容块）
     */
    public static ConversationMessage assistant(List<ContentBlock> blocks) {
        return new ConversationMessage(MessageRole.ASSISTANT, blocks, null, Instant.now());
    }

    /**
     * 创建助手消息（带Token使用统计）
     */
    public static ConversationMessage assistantWithUsage(List<ContentBlock> blocks, TokenUsage usage) {
        return new ConversationMessage(MessageRole.ASSISTANT, blocks, usage, Instant.now());
    }

    /**
     * 创建工具结果消息
     */
    public static ConversationMessage toolResult(String toolUseId, String toolName, String output, boolean isError) {
        return new ConversationMessage(
            MessageRole.TOOL,
            List.of(new ToolResultBlock(toolUseId, toolName, output, isError)),
            null,
            Instant.now()
        );
    }

    /**
     * 创建系统消息
     */
    public static ConversationMessage system(String content) {
        return new ConversationMessage(
            MessageRole.SYSTEM,
            List.of(new TextBlock(content)),
            null,
            Instant.now()
        );
    }

    /**
     * 获取内容预览（用于摘要）
     */
    @JsonIgnore
    public String preview(int maxChars) {
        String text = blocks.stream()
            .map(ContentBlock::asText)
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    /**
     * 检查是否包含工具调用
     */
    @JsonIgnore
    public boolean hasToolUse() {
        return blocks.stream().anyMatch(b -> b instanceof ToolUseBlock);
    }

    /**
     * 检查是否包含工具结果
     */
    @JsonIgnore
    public boolean hasToolResult() {
        return blocks.stream().anyMatch(b -> b instanceof ToolResultBlock);
    }

    /**
     * 提取所有工具名称
     */
    @JsonIgnore
    public List<String> extractToolNames() {
        return blocks.stream()
            .<String>mapMulti((b, consumer) -> {
                if (b instanceof ToolUseBlock t) {
                    consumer.accept(t.name());
                } else if (b instanceof ToolResultBlock t) {
                    consumer.accept(t.toolName());
                }
            })
            .toList();
    }
}
