package com.openmanus.saa.service.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从对话中提取用户偏好。
 */
@Component
public class PreferenceExtractor {

    private static final Logger log = LoggerFactory.getLogger(PreferenceExtractor.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PreferenceExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从最近的对话中提取用户偏好。
     *
     * @param messages 对话消息列表
     * @param currentObjective 当前目标（可选）
     * @return 提取的偏好 Map
     */
    public Map<String, Object> extractPreferences(List<ConversationMessage> messages, String currentObjective) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }

        // 获取最近几轮用户消息
        List<String> userInputs = messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .map(this::extractText)
            .filter(t -> t != null && !t.isBlank())
            .toList();

        if (userInputs.isEmpty()) {
            return Map.of();
        }

        try {
            String content = chatClient.prompt()
                .system("""
                    Extract user preferences from the conversation.
                    Look for explicit preferences like:
                    - Dietary restrictions (e.g., "我喜欢吃辣", "I don't eat seafood")
                    - Budget preferences (e.g., "预算500元", "mid-range")
                    - Travel style (e.g., "深度游", "relaxing vacation")
                    - Language preference
                    - Any other preferences that should be remembered

                    Return JSON only in this format:
                    {
                      "preferences": {
                        "diet": ["喜欢吃辣", "不吃海鲜"],
                        "budget": "中等",
                        "travel_style": "深度游"
                      }
                    }

                    If no preferences found, return: {"preferences": {}}
                    Only extract EXPLICITLY stated preferences, do not infer.
                    """)
                .user("""
                    Recent user messages:
                    %s

                    Current objective:
                    %s
                    """.formatted(String.join("\n", userInputs), currentObjective != null ? currentObjective : "N/A"))
                .call()
                .content();

            if (content == null || content.isBlank()) {
                return Map.of();
            }

            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);
            JsonNode prefsNode = root.path("preferences");

            if (!prefsNode.isObject()) {
                return Map.of();
            }

            Map<String, Object> result = new HashMap<>();
            prefsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    List<String> list = new ArrayList<>();
                    value.forEach(item -> list.add(item.asText()));
                    result.put(entry.getKey(), List.copyOf(list));
                } else {
                    result.put(entry.getKey(), value.asText());
                }
            });

            log.info("Extracted {} preferences from conversation", result.size());
            return Map.copyOf(result);

        } catch (Exception e) {
            log.warn("Failed to extract preferences", e);
            return Map.of();
        }
    }

    private String extractText(ConversationMessage message) {
        return message.blocks().stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .filter(t -> t != null && !t.isBlank())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
