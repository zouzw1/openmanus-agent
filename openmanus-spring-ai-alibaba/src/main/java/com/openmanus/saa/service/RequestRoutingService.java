package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class RequestRoutingService {

    private static final Logger log = LoggerFactory.getLogger(RequestRoutingService.class);

    public enum RouteMode {
        DIRECT_CHAT,
        PLAN_EXECUTE
    }

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public RequestRoutingService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    public RouteMode decideChatOrPlan(String prompt) {
        return toLegacyMode(resolveDefaultIntent(prompt).routeMode());
    }

    public IntentResolution resolveDefaultIntent(String prompt) {
        String content = chatClient.prompt()
                .system("""
                        You are a routing node for an AI agent system.

                        Decide whether the user request should:
                        - DIRECT_CHAT: respond directly in one answer without making a plan first.
                        - PLAN_EXECUTE: first create a plan, then execute it.

                        Choose DIRECT_CHAT when the request is:
                        - greeting or small talk
                        - self-introduction or capability question
                        - simple conversational Q&A
                        - a direct explanation that does not require tool orchestration or multi-step execution

                        Choose PLAN_EXECUTE when the request requires:
                        - multiple meaningful steps
                        - tool usage
                        - file or project inspection
                        - data retrieval and then processing
                        - execution, modification, generation, analysis, or verification work
                        - creating or producing a concrete deliverable such as a file, document, report, markdown, word file, ppt, spreadsheet, or saved output
                        - requests that mention a "plan" but also ask you to generate, write, export, format, save, or deliver the result

                        Examples:
                        - "hello" -> DIRECT_CHAT
                        - "give me a study plan for next week" -> PLAN_EXECUTE
                        - "I want to go to Beijing, make me a plan and deliver it as a Word document" -> PLAN_EXECUTE
                        - "analyze this project and summarize module responsibilities" -> PLAN_EXECUTE

                        Return JSON only in this exact format:
                        {
                          "mode": "DIRECT_CHAT",
                          "reason": "short explanation"
                        }
                        """)
                .user("""
                        User request:
                        %s
                        """.formatted(prompt))
                .call()
                .content();

        IntentRouteMode routeMode = parseMode(content);
        return new IntentResolution(
                routeMode == IntentRouteMode.DIRECT_CHAT ? "default_direct_chat" : "default_plan_execute",
                0.6d,
                routeMode,
                null,
                java.util.Map.of(),
                java.util.List.of()
        );
    }

    private IntentRouteMode parseMode(String content) {
        if (content == null || content.isBlank()) {
            return IntentRouteMode.PLAN_EXECUTE;
        }

        String normalized = stripMarkdownCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String mode = root.path("mode").asText("");
            return IntentRouteMode.valueOf(mode);
        } catch (Exception e) {
            log.warn("Failed to parse routing decision as JSON, falling back to text parsing: {}", content, e);
        }

        String upper = normalized.toUpperCase();
        if (upper.contains("DIRECT_CHAT")) {
            return IntentRouteMode.DIRECT_CHAT;
        }
        if (upper.contains("PLAN_EXECUTE")) {
            return IntentRouteMode.PLAN_EXECUTE;
        }
        return IntentRouteMode.PLAN_EXECUTE;
    }

    private RouteMode toLegacyMode(IntentRouteMode routeMode) {
        return routeMode == IntentRouteMode.DIRECT_CHAT ? RouteMode.DIRECT_CHAT : RouteMode.PLAN_EXECUTE;
    }

    private String stripMarkdownCodeFence(String content) {
        return content.replace("```json", "").replace("```", "").trim();
    }
}
