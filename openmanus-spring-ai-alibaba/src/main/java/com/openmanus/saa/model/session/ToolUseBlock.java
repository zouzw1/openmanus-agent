package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ToolUseBlock(String id, String name, String input) implements ContentBlock {
    @Override
    @JsonIgnore
    public BlockType getType() {
        return BlockType.TOOL_USE;
    }

    @Override
    @JsonIgnore
    public String toSummary() {
        return "tool_use " + name + "(" + ContentBlock.truncate(input, 100) + ")";
    }
}