package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockTest {

    @Test
    void textBlockReturnsCorrectType() {
        TextBlock block = new TextBlock("Hello");

        assertThat(block.getType()).isEqualTo(BlockType.TEXT);
        assertThat(block.text()).isEqualTo("Hello");
    }

    @Test
    void textBlockToSummaryTruncatesLongText() {
        String longText = "x".repeat(200);
        TextBlock block = new TextBlock(longText);

        String summary = block.toSummary();

        assertThat(summary).hasSizeLessThanOrEqualTo(161);
        assertThat(summary).endsWith("…");
    }

    @Test
    void toolUseBlockReturnsCorrectType() {
        ToolUseBlock block = new ToolUseBlock("id-1", "bash", "echo hello");

        assertThat(block.getType()).isEqualTo(BlockType.TOOL_USE);
        assertThat(block.id()).isEqualTo("id-1");
        assertThat(block.name()).isEqualTo("bash");
        assertThat(block.input()).isEqualTo("echo hello");
    }

    @Test
    void toolUseBlockToSummaryContainsToolName() {
        ToolUseBlock block = new ToolUseBlock("id-1", "bash", "echo hello");

        String summary = block.toSummary();

        assertThat(summary).contains("tool_use");
        assertThat(summary).contains("bash");
    }

    @Test
    void toolResultBlockReturnsCorrectType() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "hello", false);

        assertThat(block.getType()).isEqualTo(BlockType.TOOL_RESULT);
        assertThat(block.toolUseId()).isEqualTo("id-1");
        assertThat(block.isError()).isFalse();
    }

    @Test
    void toolResultBlockToSummaryContainsErrorWhenIsError() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "failed", true);

        String summary = block.toSummary();

        assertThat(summary).contains("error");
        assertThat(summary).contains("tool_result");
    }

    @Test
    void toolResultBlockToSummaryOmitsErrorWhenNotError() {
        ToolResultBlock block = new ToolResultBlock("id-1", "bash", "success", false);

        String summary = block.toSummary();

        assertThat(summary).doesNotContain("error");
    }
}