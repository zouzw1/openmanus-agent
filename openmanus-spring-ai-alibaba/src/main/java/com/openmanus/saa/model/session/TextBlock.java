package com.openmanus.saa.model.session;

public record TextBlock(String text) implements ContentBlock {
    @Override
    public BlockType getType() {
        return BlockType.TEXT;
    }

    @Override
    public String toSummary() {
        return truncate(text, 160);
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}