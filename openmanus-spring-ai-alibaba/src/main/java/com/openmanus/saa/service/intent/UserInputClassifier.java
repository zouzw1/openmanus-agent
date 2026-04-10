package com.openmanus.saa.service.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.InputClassification;
import com.openmanus.saa.model.UserInputIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户输入意图分类器。
 * 基于 LLM 判断用户输入是对反馈的回答还是新任务。
 */
@Service
public class UserInputClassifier {

    private static final Logger log = LoggerFactory.getLogger(UserInputClassifier.class);
    private static final List<String> CONTINUE_KEYWORDS = List.of(
        "继续", "继续执行", "继续吧", "go on", "continue",
        "用默认的", "不用管", "继续用", "跳过", "skip",
        "好的", "可以", "没问题", "ok", "yes"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public UserInputClassifier(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 分类用户输入意图。
     */
    public InputClassification classify(String prompt, HumanFeedbackRequest pendingFeedback) {
        if (prompt == null || prompt.isBlank()) {
            return InputClassification.uncertain("Empty input");
        }

        // 1. 规则优先：检查是否明确要求继续
        if (isExplicitContinue(prompt)) {
            log.info("User input detected as CONTINUE via rule matching");
            return InputClassification.continueExecution();
        }

        // 2. 规则检查：是否能提取参数
        Map<String, Object> extractedParams = extractParametersByRule(prompt, pendingFeedback);
        if (!extractedParams.isEmpty()) {
            log.info("User input detected as SUPPLEMENT_INFO with params: {}", extractedParams);
            return InputClassification.supplementInfo(extractedParams);
        }

        // 3. LLM 分类：判断是否新任务
        return classifyWithLLM(prompt, pendingFeedback);
    }

    /**
     * 规则判断：是否明确要求继续。
     */
    private boolean isExplicitContinue(String prompt) {
        String lower = prompt.toLowerCase().trim();
        return CONTINUE_KEYWORDS.stream()
            .anyMatch(keyword -> lower.contains(keyword.toLowerCase()));
    }

    /**
     * 规则提取：从用户输入中提取参数。
     */
    private Map<String, Object> extractParametersByRule(String prompt, HumanFeedbackRequest pendingFeedback) {
        Map<String, Object> params = new HashMap<>();

        if (pendingFeedback == null || pendingFeedback.getErrorMessage() == null) {
            return params;
        }

        String errorMessage = pendingFeedback.getErrorMessage();

        // 提取日期（匹配 "是4月8"、"4月8日"、"下周1" 等）
        if (errorMessage.contains("出发日期") || errorMessage.contains("departureDate")) {
            // 匹配日期模式
            Pattern datePattern = Pattern.compile("(\\d{1,2}月\\d{1,2}[日号]?|下周[一二三四五六日]|\\d{4}-\\d{2}-\\d{2})");
            Matcher matcher = datePattern.matcher(prompt);
            if (matcher.find()) {
                params.put("departureDate", matcher.group(1));
            }
        }

        // 提取购物偏好
        if (errorMessage.contains("购物") || errorMessage.contains("shopping")) {
            String[] shoppingAreas = {"新街口", "老门东", "河西奥体", "夫子庙"};
            for (String area : shoppingAreas) {
                if (prompt.contains(area)) {
                    params.put("shoppingPreference", area);
                    break;
                }
            }
        }

        // 提取酒店要求
        if (errorMessage.contains("酒店") || errorMessage.contains("hotel")) {
            if (prompt.contains("近地铁") || prompt.contains("地铁口")) {
                params.put("hotelCriteria", "near_subway");
            } else if (prompt.contains("含早餐")) {
                params.put("hotelCriteria", "with_breakfast");
            }
        }

        // 如果用户说"没特别"或"没有特别要求"，标记为使用默认值
        if (prompt.contains("没特别") || prompt.contains("没有特别") || prompt.contains("无所谓")) {
            params.put("useDefault", true);
        }

        return params;
    }

    /**
     * LLM 分类：判断用户输入意图。
     */
    private InputClassification classifyWithLLM(String prompt, HumanFeedbackRequest pendingFeedback) {
        String systemPrompt = """
            你是一个用户意图分类助手。你需要判断用户的输入是：
            1. SUPPLEMENT_INFO：回答之前的问题（提供信息）
            2. CONTINUE：要求继续执行（使用默认值）
            3. NEW_TASK：开始一个新的独立任务

            返回 JSON 格式：{"intent": "SUPPLEMENT_INFO|CONTINUE|NEW_TASK", "reasoning": "简要原因"}
            """;

        String userPrompt = String.format("""
            之前的任务：%s
            系统的问题：%s
            用户的输入：%s

            请判断用户的意图。
            """,
            pendingFeedback != null ? pendingFeedback.getObjective() : "无",
            pendingFeedback != null ? pendingFeedback.getErrorMessage() : "无",
            prompt
        );

        try {
            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

            log.debug("LLM classification response: {}", response);

            JsonNode node = objectMapper.readTree(extractJson(response));
            String intentStr = node.get("intent").asText();
            UserInputIntent intent = UserInputIntent.valueOf(intentStr);

            return new InputClassification(intent, Map.of(), node.get("reasoning").asText());
        } catch (Exception e) {
            log.warn("LLM classification failed, defaulting to SUPPLEMENT_INFO", e);
            // 默认假设用户是在回答问题
            return InputClassification.supplementInfo(Map.of());
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}