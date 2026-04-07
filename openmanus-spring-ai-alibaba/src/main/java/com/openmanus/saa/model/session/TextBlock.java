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
        return ContentBlock.truncate(text, 160);
    }
}