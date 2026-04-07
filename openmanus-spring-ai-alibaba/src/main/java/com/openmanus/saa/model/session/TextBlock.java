package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TextBlock(String text) implements ContentBlock {
    @Override
    @JsonIgnore
    public BlockType getType() {
        return BlockType.TEXT;
    }

    @Override
    @JsonIgnore
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