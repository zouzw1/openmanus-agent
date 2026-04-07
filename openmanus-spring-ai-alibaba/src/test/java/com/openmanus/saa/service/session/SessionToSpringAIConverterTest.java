package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SessionToSpringAIConverterTest {

    private SessionToSpringAIConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SessionToSpringAIConverter();
    }

    @Test
    void convertSystemMessage() {
        Session session = new Session("test");
        session = session.addSystemMessage("You are a helpful assistant");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    void convertUserMessage() {
        Session session = new Session("test");
        session = session.addUserMessage("Hello");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void convertAssistantMessage() {
        Session session = new Session("test");
        session = session.addAssistantMessage("I can help you");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void convertToolResultBlock() {
        Session session = new Session("test");
        session = session.addMessage(ConversationMessage.toolResult("id-1", "bash", "hello world", false));

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void convertMixedMessages() {
        Session session = new Session("test");
        session = session.addSystemMessage("System");
        session = session.addUserMessage("User");
        session = session.addAssistantMessage("Assistant");

        List<Message> result = converter.convert(session);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(1)).isInstanceOf(UserMessage.class);
        assertThat(result.get(2)).isInstanceOf(AssistantMessage.class);
    }
}
