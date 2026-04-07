package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ToolResultBlock(
    String toolUseId,
    String toolName,
    String output,
    boolean isError
) implements ContentBlock {
    @Override
    @JsonIgnore
    public BlockType getType() {
        return BlockType.TOOL_RESULT;
    }

    @Override
    @JsonIgnore
    public String toSummary() {
        String prefix = isError ? "error " : "";
        return prefix + "tool_result " + toolName + ": " + truncate(output, 100);
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}