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

    /**
     * 截断字符串到指定最大长度。
     * 供实现类在 toSummary() 中使用。
     *
     * @param content 要截断的字符串
     * @param maxChars 最大字符数
     * @return 截断后的字符串，超长时末尾添加省略号
     */
    static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}