package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class RequestRoutingService {

    private static final Logger log = LoggerFactory.getLogger(RequestRoutingService.class);

    public enum RouteMode {
        DIRECT_CHAT,
        PLAN_ONLY,
        PLAN_EXECUTE
    }

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public RequestRoutingService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    public RouteMode decideChatOrPlan(String prompt) {
        String content = chatClient.prompt()
                .system("""
                        You are a routing node for an AI agent system.

                        Decide whether the user request should:
                        - DIRECT_CHAT: respond directly in one answer without making a plan first.
                        - PLAN_ONLY: generate a human-readable plan, but do not execute it.
                        - PLAN_EXECUTE: first create a plan, then execute it.

                        Choose DIRECT_CHAT when the request is:
                        - greeting or small talk
                        - self-introduction or capability question
                        - simple conversational Q&A
                        - a direct explanation that does not require tool orchestration or multi-step execution

                        Choose PLAN_ONLY when the request is:
                        - asking for a plan, outline, checklist, roadmap, schedule, or strategy
                        - asking to organize work before execution
                        - asking to review or confirm the plan first
                        - not asking you to actually carry out the steps right now

                        Choose PLAN_EXECUTE when the request requires:
                        - multiple meaningful steps
                        - tool usage
                        - file or project inspection
                        - data retrieval and then processing
                        - execution, modification, generation, analysis, or verification work

                        Examples:
                        - "hello" -> DIRECT_CHAT
                        - "give me a study plan for next week" -> PLAN_ONLY
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

        return parseMode(content);
    }

    private RouteMode parseMode(String content) {
        if (content == null || content.isBlank()) {
            return RouteMode.PLAN_EXECUTE;
        }

        String normalized = stripMarkdownCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String mode = root.path("mode").asText("");
            return RouteMode.valueOf(mode);
        } catch (Exception e) {
            log.warn("Failed to parse routing decision as JSON, falling back to text parsing: {}", content, e);
        }

        String upper = normalized.toUpperCase();
        if (upper.contains("DIRECT_CHAT")) {
            return RouteMode.DIRECT_CHAT;
        }
        if (upper.contains("PLAN_ONLY")) {
            return RouteMode.PLAN_ONLY;
        }
        if (upper.contains("PLAN_EXECUTE")) {
            return RouteMode.PLAN_EXECUTE;
        }
        return RouteMode.PLAN_EXECUTE;
    }

    private String stripMarkdownCodeFence(String content) {
        return content.replace("```json", "").replace("```", "").trim();
    }
}
