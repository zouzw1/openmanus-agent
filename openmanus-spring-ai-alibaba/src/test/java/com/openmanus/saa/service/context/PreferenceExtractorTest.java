package com.openmanus.saa.service.context;

import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenceExtractorTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Test
    void extractPreferences_withUserPreferences_shouldReturnMap() {
        // Setup mock
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
            ```json
            {"preferences": {"diet": ["喜欢吃辣"], "budget": "中等"}}
            ```
            """);

        PreferenceExtractor extractor = new PreferenceExtractor(chatClient);

        List<ConversationMessage> messages = List.of(
            ConversationMessage.userText("我喜欢吃辣，预算中等")
        );

        Map<String, Object> prefs = extractor.extractPreferences(messages, "旅行计划");

        assertNotNull(prefs);
        assertTrue(prefs.containsKey("diet"));
    }

    @Test
    void extractPreferences_emptyMessages_shouldReturnEmptyMap() {
        PreferenceExtractor extractor = new PreferenceExtractor(chatClient);
        Map<String, Object> prefs = extractor.extractPreferences(List.of(), null);
        assertTrue(prefs.isEmpty());
    }
}
