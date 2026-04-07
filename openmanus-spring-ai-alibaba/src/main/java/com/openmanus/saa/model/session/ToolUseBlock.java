package com.openmanus.saa.model.session;

public record ToolUseBlock(String id, String name, String input) implements ContentBlock {
    @Override
    public BlockType getType() {
        return BlockType.TOOL_USE;
    }

    @Override
    public String toSummary() {
        return "tool_use " + name + "(" + truncate(input, 100) + ")";
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}