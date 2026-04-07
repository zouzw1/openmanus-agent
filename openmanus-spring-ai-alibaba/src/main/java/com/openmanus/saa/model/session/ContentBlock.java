package com.openmanus.saa.model.session;

public sealed interface ContentBlock permits TextBlock, ToolUseBlock, ToolResultBlock {
    String toSummary();
    BlockType getType();

    /**
     * 获取内容块的文本表示（用于摘要和显示）
     */
    default String asText() {
        if (this instanceof TextBlock t) {
            return t.text();
        } else if (this instanceof ToolUseBlock t) {
            return "[" + t.name() + "] " + t.input();
        } else if (this instanceof ToolResultBlock t) {
            return t.output();
        }
        return "";
    }
}