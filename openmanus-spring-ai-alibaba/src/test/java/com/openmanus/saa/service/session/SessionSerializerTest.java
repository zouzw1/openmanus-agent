package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionSerializerTest {

    private SessionSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // For Java 8 time module
        serializer = new SessionSerializer(objectMapper);
    }

    @Test
    void serializeAndDeserializeEmptySession() {
        Session session = new Session("test-session");

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.sessionId()).isEqualTo("test-session");
        assertThat(deserialized.version()).isEqualTo(1);
        assertThat(deserialized.messages()).isEmpty();
    }

    @Test
    void serializeAndDeserializeWithMessages() {
        Session session = new Session("test-session");
        session = session.addMessage(ConversationMessage.userText("Hello"));
        session = session.addMessage(ConversationMessage.assistant(List.of(new TextBlock("Hi there"))));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.messages()).hasSize(2);
        assertThat(deserialized.messages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(deserialized.messages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void serializeAndDeserializeWithToolBlocks() {
        Session session = new Session("test-session");
        session = session.addMessage(ConversationMessage.assistantWithUsage(
            List.of(new ToolUseBlock("id-1", "bash", "echo hello")),
            new TokenUsage(100, 50, 10, 5)
        ));
        session = session.addMessage(ConversationMessage.toolResult("id-1", "bash", "hello", false));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.messages()).hasSize(2);
        assertThat(deserialized.messages().get(0).blocks().get(0)).isInstanceOf(ToolUseBlock.class);
        assertThat(deserialized.messages().get(1).blocks().get(0)).isInstanceOf(ToolResultBlock.class);
        assertThat(deserialized.messages().get(0).usage()).isNotNull();
    }

    @Test
    void serializeAndDeserializeWithWorkingMemory() {
        Session session = new Session("test-session");
        session = session.putMemory("key1", "value1");
        session = session.putMemory("key2", 42);

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.getMemory("key1")).contains("value1");
        assertThat(deserialized.getMemory("key2")).contains(42);
    }

    @Test
    void serializeAndDeserializeWithCumulativeUsage() {
        Session session = new Session("test-session");
        session = session.updateCumulativeUsage(new TokenUsage(100, 50, 10, 5));

        String json = serializer.toJson(session);
        Session deserialized = serializer.fromJson(json);

        assertThat(deserialized.cumulativeUsage()).isEqualTo(new TokenUsage(100, 50, 10, 5));
    }
}
