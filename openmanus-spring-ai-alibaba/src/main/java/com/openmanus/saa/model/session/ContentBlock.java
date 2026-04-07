package com.openmanus.saa.model.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ToolUseBlock.class, name = "tool_use"),
    @JsonSubTypes.Type(value = ToolResultBlock.class, name = "tool_result")
})
public sealed interface ContentBlock permits TextBlock, ToolUseBlock, ToolResultBlock {
    @JsonIgnore
    String toSummary();
    @JsonIgnore
    BlockType getType();

    /**
     * 获取内容块的文本表示（用于摘要和显示）
     */
    @JsonIgnore
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