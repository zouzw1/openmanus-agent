package com.openmanus.saa.model.session;

import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.ResponseMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void createSessionWithDefaults() {
        Session session = new Session("test-session-id");

        assertThat(session.sessionId()).isEqualTo("test-session-id");
        assertThat(session.version()).isEqualTo(1);
        assertThat(session.messages()).isEmpty();
        assertThat(session.workingMemory()).isEmpty();
        assertThat(session.executionLog()).isEmpty();
        assertThat(session.cumulativeUsage()).isEqualTo(TokenUsage.zero());
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.updatedAt()).isNotNull();
        assertThat(session.lastAccessedAt()).isNotNull();
    }

    @Test
    void addMessageIncreasesMessageCount() {
        Session session = new Session("test-id");
        ConversationMessage msg = ConversationMessage.userText("Hello");

        Session updated = session.addMessage(msg);

        assertThat(updated.messages()).hasSize(1);
        assertThat(updated.messages().get(0)).isEqualTo(msg);
        assertThat(session.messages()).isEmpty(); // 原会话不可变
    }

    @Test
    void addSystemMessageAddsSystemMessage() {
        Session session = new Session("test-id");

        Session updated = session.addSystemMessage("System prompt");

        assertThat(updated.messages()).hasSize(1);
        ConversationMessage msg = updated.messages().get(0);
        assertThat(msg.role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("System prompt");
    }

    @Test
    void addUserMessageAddsUserMessage() {
        Session session = new Session("test-id");

        Session updated = session.addUserMessage("User input");

        assertThat(updated.messages()).hasSize(1);
        ConversationMessage msg = updated.messages().get(0);
        assertThat(msg.role()).isEqualTo(MessageRole.USER);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("User input");
    }

    @Test
    void addAssistantMessageAddsAssistantMessage() {
        Session session = new Session("test-id");

        Session updated = session.addAssistantMessage("Assistant response");

        assertThat(updated.messages()).hasSize(1);
        ConversationMessage msg = updated.messages().get(0);
        assertThat(msg.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(((TextBlock) msg.blocks().get(0)).text()).isEqualTo("Assistant response");
    }

    @Test
    void recordToolCallAddsToolUseMessage() {
        Session session = new Session("test-id");

        Session updated = session.recordToolCall("bash", "echo hello");

        assertThat(updated.messages()).hasSize(1);
        ConversationMessage msg = updated.messages().get(0);
        assertThat(msg.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(msg.blocks().get(0)).isInstanceOf(ToolUseBlock.class);
        ToolUseBlock toolUse = (ToolUseBlock) msg.blocks().get(0);
        assertThat(toolUse.name()).isEqualTo("bash");
        assertThat(toolUse.input()).isEqualTo("echo hello");
        assertThat(toolUse.id()).startsWith("tool-");
    }

    @Test
    void recordToolResultAddsToolResultMessage() {
        Session session = new Session("test-id");

        Session updated = session.recordToolResult("tool-123", "bash", "output", false);

        assertThat(updated.messages()).hasSize(1);
        ConversationMessage msg = updated.messages().get(0);
        assertThat(msg.role()).isEqualTo(MessageRole.TOOL);
        assertThat(msg.blocks().get(0)).isInstanceOf(ToolResultBlock.class);
        ToolResultBlock result = (ToolResultBlock) msg.blocks().get(0);
        assertThat(result.toolUseId()).isEqualTo("tool-123");
        assertThat(result.toolName()).isEqualTo("bash");
        assertThat(result.output()).isEqualTo("output");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void putAndGetMemory() {
        Session session = new Session("test-id");

        Session updated = session.putMemory("key1", "value1");

        assertThat(updated.getMemory("key1")).contains("value1");
        assertThat(session.getMemory("key1")).isEmpty(); // 原会话不可变
    }

    @Test
    void typedMemoryReturnsCorrectType() {
        Session session = new Session("test-id");
        Session updated = session.putMemory("count", 42);

        assertThat(updated.getMemory("count", Integer.class)).contains(42);
        assertThat(updated.getMemory("count", String.class)).isEmpty(); // 类型不匹配
    }

    @Test
    void removeMemoryRemovesEntry() {
        Session session = new Session("test-id")
            .putMemory("key1", "value1")
            .putMemory("key2", "value2");

        Session updated = session.removeMemory("key1");

        assertThat(updated.getMemory("key1")).isEmpty();
        assertThat(updated.getMemory("key2")).contains("value2");
        assertThat(session.getMemory("key1")).contains("value1"); // 原会话不变
    }

    @Test
    void addExecutionLogAddsEntry() {
        Session session = new Session("test-id");

        Session updated = session.addExecutionLog("Step 1: started");

        assertThat(updated.executionLog()).containsExactly("Step 1: started");
        assertThat(updated.executionLog()).hasSize(1);
        assertThat(session.executionLog()).isEmpty(); // 原会话不可变
    }

    @Test
    void isEmptyReturnsTrueForNewSession() {
        Session session = new Session("test-id");

        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenHasMessages() {
        Session session = new Session("test-id").addUserMessage("Hello");

        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void isEmptyReturnsFalseWhenHasWorkingMemory() {
        Session session = new Session("test-id").putMemory("key", "value");

        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void isEmptyReturnsFalseWhenHasExecutionLog() {
        Session session = new Session("test-id").addExecutionLog("log entry");

        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void clearRemovesAllData() {
        Session session = new Session("test-id")
            .addUserMessage("Hello")
            .putMemory("key", "value")
            .addExecutionLog("log")
            .updateCumulativeUsage(new TokenUsage(100, 50, 10, 5));

        Session cleared = session.clear();

        assertThat(cleared.messages()).isEmpty();
        assertThat(cleared.workingMemory()).isEmpty();
        assertThat(cleared.executionLog()).isEmpty();
        assertThat(cleared.cumulativeUsage()).isEqualTo(TokenUsage.zero());
        assertThat(cleared.sessionId()).isEqualTo("test-id"); // sessionId 保留
        assertThat(cleared.version()).isEqualTo(session.version()); // version 保留
    }

    @Test
    void estimateTokensCalculatesApproximately() {
        Session session = new Session("test-id")
            .addUserMessage("Hello world") // 约 3 tokens
            .addAssistantMessage("This is a response from the assistant"); // 约 9 tokens

        int estimated = session.estimateTokens();

        // 估算基于字符数/4，预期约 12+ tokens
        assertThat(estimated).isPositive();
        assertThat(estimated).isGreaterThan(5);
    }

    @Test
    void updateCumulativeUsageAddsToTotal() {
        Session session = new Session("test-id");

        Session updated = session
            .updateCumulativeUsage(new TokenUsage(100, 50, 10, 5))
            .updateCumulativeUsage(new TokenUsage(50, 25, 5, 3));

        assertThat(updated.cumulativeUsage().inputTokens()).isEqualTo(150);
        assertThat(updated.cumulativeUsage().outputTokens()).isEqualTo(75);
        assertThat(updated.cumulativeUsage().cacheCreationInputTokens()).isEqualTo(15);
        assertThat(updated.cumulativeUsage().cacheReadInputTokens()).isEqualTo(8);
    }

    @Test
    void withMessagesCreatesNewSessionWithMessages() {
        Session session = new Session("test-id")
            .addUserMessage("Old message");

        List<ConversationMessage> newMessages = List.of(
            ConversationMessage.system("System"),
            ConversationMessage.userText("New message")
        );

        Session updated = session.withMessages(newMessages);

        assertThat(updated.messages()).hasSize(2);
        assertThat(updated.messages().get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(updated.messages().get(1).role()).isEqualTo(MessageRole.USER);
        assertThat(session.messages()).hasSize(1); // 原会话不变
    }

    @Test
    void getAllMemoryReturnsImmutableCopy() {
        Session session = new Session("test-id")
            .putMemory("key1", "value1")
            .putMemory("key2", "value2");

        Map<String, Object> memory = session.getAllMemory();

        assertThat(memory).hasSize(2);
        assertThat(memory).containsEntry("key1", "value1");
        assertThat(memory).containsEntry("key2", "value2");
    }

    @Test
    void getExecutionLogListReturnsImmutableCopy() {
        Session session = new Session("test-id")
            .addExecutionLog("log1")
            .addExecutionLog("log2");

        List<String> log = session.getExecutionLogList();

        assertThat(log).containsExactly("log1", "log2");
    }

    @Test
    void fullConstructorWithAllFields() {
        Instant now = Instant.now();
        List<ConversationMessage> messages = List.of(ConversationMessage.userText("Hello"));
        Map<String, Object> memory = Map.of("key", "value");
        List<String> log = List.of("entry");
        TokenUsage usage = new TokenUsage(100, 50, 10, 5);
        InferencePolicy policy = InferencePolicy.none();

        Session session = new Session(
            2, "full-session", now, now, now,
            messages, memory, log, usage, policy, ResponseMode.FINAL_DELIVERABLE
        );

        assertThat(session.version()).isEqualTo(2);
        assertThat(session.sessionId()).isEqualTo("full-session");
        assertThat(session.messages()).hasSize(1);
        assertThat(session.workingMemory()).containsEntry("key", "value");
        assertThat(session.executionLog()).containsExactly("entry");
        assertThat(session.cumulativeUsage()).isEqualTo(usage);
        assertThat(session.latestInferencePolicy()).isEqualTo(policy);
        assertThat(session.latestResponseMode()).isEqualTo(ResponseMode.FINAL_DELIVERABLE);
    }

    @Test
    void versionDefaultsToOneWhenZeroOrNegative() {
        Session sessionZero = new Session(0, "id", null, null, null, null, null, null, null, null, null);
        Session sessionNeg = new Session(-5, "id", null, null, null, null, null, null, null, null, null);

        assertThat(sessionZero.version()).isEqualTo(1);
        assertThat(sessionNeg.version()).isEqualTo(1);
    }

    @Test
    void nullDefaultsAreApplied() {
        Session session = new Session(1, "id", null, null, null, null, null, null, null, null, null);

        assertThat(session.createdAt()).isNotNull();
        assertThat(session.updatedAt()).isNotNull();
        assertThat(session.lastAccessedAt()).isNotNull();
        assertThat(session.messages()).isEmpty();
        assertThat(session.workingMemory()).isEmpty();
        assertThat(session.executionLog()).isEmpty();
        assertThat(session.cumulativeUsage()).isEqualTo(TokenUsage.zero());
        assertThat(session.latestInferencePolicy()).isNull();
        assertThat(session.latestResponseMode()).isNull();
    }
}