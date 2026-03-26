package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.util.IntentResolutionHelper;
import java.util.Map;
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
        RoutingDecision decision = resolveRoutingDecision(prompt);
        return new IntentResolution(
                decision.routeMode() == IntentRouteMode.DIRECT_CHAT ? "default_direct_chat" : "default_plan_execute",
                0.6d,
                decision.routeMode(),
                null,
                Map.of(IntentResolutionHelper.RESPONSE_MODE_ATTRIBUTE, decision.responseMode().name()),
                java.util.List.of()
        );
    }

    public ResponseMode inferResponseMode(String prompt) {
        return resolveRoutingDecision(prompt).responseMode();
    }

    private RoutingDecision resolveRoutingDecision(String prompt) {
        String content = chatClient.prompt()
                .system("""
                        You are a routing node for an AI agent system.

                        Decide whether the user request should:
                        - DIRECT_CHAT: respond directly in one answer without making a plan first.
                        - PLAN_EXECUTE: first create a plan, then execute it.

                        Also decide the preferred final response style for successful PLAN_EXECUTE requests:
                        - FINAL_DELIVERABLE: the main answer should focus on the final artifact, generated content, checklist, plan, roadmap, document, or other deliverable itself.
                        - WORKFLOW_SUMMARY: the main answer should focus on what the system did, how the workflow proceeded, and the execution summary.
                        - HYBRID: include the final deliverable first, then a concise workflow summary.

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
                        - "give me a Java study roadmap with a checklist" -> FINAL_DELIVERABLE
                        - "summarize what you did step by step" -> WORKFLOW_SUMMARY
                        - "generate the file and briefly explain the process" -> HYBRID

                        Return JSON only in this exact format:
                        {
                          "mode": "DIRECT_CHAT",
                          "responseMode": "HYBRID",
                          "reason": "short explanation"
                        }
                        """)
                .user("""
                        User request:
                        %s
                        """.formatted(prompt))
                .call()
                .content();

        return parseRoutingDecision(content);
    }

    private RoutingDecision parseRoutingDecision(String content) {
        if (content == null || content.isBlank()) {
            return new RoutingDecision(IntentRouteMode.PLAN_EXECUTE, ResponseMode.HYBRID);
        }

        String normalized = stripMarkdownCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String mode = root.path("mode").asText("");
            IntentRouteMode routeMode = IntentRouteMode.valueOf(mode);
            ResponseMode responseMode = ResponseMode.from(root.path("responseMode").asText(""));
            if (responseMode == null) {
                responseMode = defaultResponseMode(routeMode);
            }
            return new RoutingDecision(routeMode, responseMode);
        } catch (Exception e) {
            log.warn("Failed to parse routing decision as JSON, falling back to text parsing: {}", content, e);
        }

        String upper = normalized.toUpperCase();
        IntentRouteMode routeMode = IntentRouteMode.PLAN_EXECUTE;
        if (upper.contains("DIRECT_CHAT")) {
            routeMode = IntentRouteMode.DIRECT_CHAT;
        } else if (upper.contains("PLAN_EXECUTE")) {
            routeMode = IntentRouteMode.PLAN_EXECUTE;
        }
        ResponseMode responseMode = parseResponseModeFromText(upper);
        return new RoutingDecision(routeMode, responseMode == null ? defaultResponseMode(routeMode) : responseMode);
    }

    private ResponseMode parseResponseModeFromText(String upper) {
        if (upper.contains("FINAL_DELIVERABLE")) {
            return ResponseMode.FINAL_DELIVERABLE;
        }
        if (upper.contains("WORKFLOW_SUMMARY")) {
            return ResponseMode.WORKFLOW_SUMMARY;
        }
        if (upper.contains("HYBRID")) {
            return ResponseMode.HYBRID;
        }
        return null;
    }

    private ResponseMode defaultResponseMode(IntentRouteMode routeMode) {
        return routeMode == IntentRouteMode.DIRECT_CHAT
                ? ResponseMode.FINAL_DELIVERABLE
                : ResponseMode.HYBRID;
    }

    private RouteMode toLegacyMode(IntentRouteMode routeMode) {
        return routeMode == IntentRouteMode.DIRECT_CHAT ? RouteMode.DIRECT_CHAT : RouteMode.PLAN_EXECUTE;
    }

    private String stripMarkdownCodeFence(String content) {
        return content.replace("```json", "").replace("```", "").trim();
    }

    private record RoutingDecision(IntentRouteMode routeMode, ResponseMode responseMode) {
    }
}
