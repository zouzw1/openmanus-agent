package com.openmanus.saa.model.session;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessageTest {

    @Test
    void userTextCreatesMessageWithTextBlock() {
        ConversationMessage msg = ConversationMessage.userText("Hello");

        assertThat(msg.role()).isEqualTo(MessageRole.USER);
        assertThat(msg.blocks()).hasSize(1);
        assertThat(msg.blocks().get(0)).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("Hello");
        assertThat(msg.usage()).isNull();
    }

    @Test
    void assistantCreatesMessageWithBlocks() {
        List<ContentBlock> blocks = List.of(
            new TextBlock("thinking"),
            new ToolUseBlock("id-1", "bash", "echo hi")
        );

        ConversationMessage msg = ConversationMessage.assistant(blocks);

        assertThat(msg.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(msg.blocks()).hasSize(2);
        assertThat(msg.usage()).isNull();
    }

    @Test
    void assistantWithUsageIncludesTokenUsage() {
        List<ContentBlock> blocks = List.of(new TextBlock("response"));
        TokenUsage usage = new TokenUsage(100, 50, 10, 5);

        ConversationMessage msg = ConversationMessage.assistantWithUsage(blocks, usage);

        assertThat(msg.usage()).isEqualTo(usage);
    }

    @Test
    void toolResultCreatesToolMessage() {
        ConversationMessage msg = ConversationMessage.toolResult("id-1", "bash", "hello", false);

        assertThat(msg.role()).isEqualTo(MessageRole.TOOL);
        assertThat(msg.blocks()).hasSize(1);
        assertThat(msg.blocks().get(0)).isInstanceOf(ToolResultBlock.class);
    }

    @Test
    void systemCreatesSystemMessage() {
        ConversationMessage msg = ConversationMessage.system("System prompt");

        assertThat(msg.role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("System prompt");
    }
}
