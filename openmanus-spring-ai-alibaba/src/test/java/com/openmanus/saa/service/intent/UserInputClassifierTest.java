package com.openmanus.saa.service.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.UserInputIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserInputClassifierTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    private UserInputClassifier classifier;

    @BeforeEach
    void setUp() {
        // Note: In real test, you would mock chatClientBuilder.build() to return chatClient
        // For now, we'll skip tests that require LLM
    }

    @Test
    void explicitContinue_shouldReturnContinue() {
        // Create classifier without LLM dependency for rule-based tests
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需要出发日期");

        InputClassification result = classifier.classify("继续", feedback);

        assertTrue(result.isContinue());
    }

    @Test
    void explicitContinue_withEnglish_shouldReturnContinue() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需要出发日期");

        InputClassification result = classifier.classify("continue", feedback);

        assertTrue(result.isContinue());
    }

    @Test
    void explicitContinue_withSkip_shouldReturnContinue() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需要出发日期");

        InputClassification result = classifier.classify("跳过", feedback);

        assertTrue(result.isContinue());
    }

    @Test
    void supplementDate_shouldExtractDate() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需用户确认：1）出发日期（下周1即2026-04-08？）");

        InputClassification result = classifier.classify("是4月8日", feedback);

        assertTrue(result.isSupplementInfo());
        assertEquals("4月8日", result.extractedParams().get("departureDate"));
    }

    @Test
    void supplementWithNoPreference_shouldMarkUseDefault() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需要出发日期和酒店要求");

        InputClassification result = classifier.classify("是4月8，其它没有特别的", feedback);

        assertTrue(result.isSupplementInfo());
        assertEquals(true, result.extractedParams().get("useDefault"));
    }

    @Test
    void supplementShoppingPreference_shouldExtractArea() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        HumanFeedbackRequest feedback = createFeedback("需确认购物偏好：新街口、老门东、河西奥体？");

        InputClassification result = classifier.classify("去老门东购物", feedback);

        assertTrue(result.isSupplementInfo());
        assertEquals("老门东", result.extractedParams().get("shoppingPreference"));
    }

    @Test
    void nullPrompt_shouldReturnUncertain() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        InputClassification result = classifier.classify(null, null);

        assertEquals(UserInputIntent.UNCERTAIN, result.intent());
    }

    @Test
    void emptyPrompt_shouldReturnUncertain() {
        classifier = new UserInputClassifier(chatClientBuilder, new ObjectMapper());

        InputClassification result = classifier.classify("   ", null);

        assertEquals(UserInputIntent.UNCERTAIN, result.intent());
    }

    private HumanFeedbackRequest createFeedback(String errorMessage) {
        return new HumanFeedbackRequest(
            "test-session",
            "旅行计划",
            "plan-123",
            0,
            null,
            null,
            errorMessage,
            "请补充参数"
        );
    }
}