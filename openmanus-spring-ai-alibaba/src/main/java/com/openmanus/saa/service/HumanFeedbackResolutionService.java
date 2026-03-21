package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
import com.openmanus.saa.util.HumanFeedbackResolver;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class HumanFeedbackResolutionService {

    private static final Logger log = LoggerFactory.getLogger(HumanFeedbackResolutionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public HumanFeedbackResolutionService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    public HumanFeedbackResponse resolve(WorkflowFeedbackRequest request, HumanFeedbackRequest pendingFeedback) {
        if (request.action() != null) {
            return HumanFeedbackResolver.buildResponse(
                    request.action(),
                    HumanFeedbackResolver.extractRawInput(request),
                    request
            );
        }

        String rawInput = HumanFeedbackResolver.extractRawInput(request);
        if (rawInput == null) {
            throw new IllegalArgumentException("Either action or userInput must be provided");
        }

        HumanFeedbackResponse.ActionType ruleAction = HumanFeedbackResolver.inferAction(rawInput);
        Optional<HumanFeedbackResponse.ActionType> llmAction = inferActionWithLlm(rawInput, pendingFeedback);
        HumanFeedbackResponse.ActionType finalAction = chooseAction(ruleAction, llmAction.orElse(null));

        log.info(
                "Resolved workflow feedback action for session {}: rule={}, llm={}, final={}",
                request.sessionId(),
                ruleAction,
                llmAction.orElse(null),
                finalAction
        );

        return HumanFeedbackResolver.buildResponse(finalAction, rawInput, request);
    }

    private HumanFeedbackResponse.ActionType chooseAction(
            HumanFeedbackResponse.ActionType ruleAction,
            HumanFeedbackResponse.ActionType llmAction
    ) {
        if (llmAction == null) {
            return ruleAction;
        }
        if (llmAction == ruleAction) {
            return ruleAction;
        }
        if (HumanFeedbackResolver.isStrongRuleAction(ruleAction)) {
            return ruleAction;
        }
        return llmAction;
    }

    private Optional<HumanFeedbackResponse.ActionType> inferActionWithLlm(
            String userInput,
            HumanFeedbackRequest pendingFeedback
    ) {
        try {
            String content = chatClient.prompt()
                    .system("""
                            You classify a user's reply to a paused workflow.

                            Return JSON only in this exact format:
                            {"action":"PROVIDE_INFO"}

                            Valid actions:
                            - PROVIDE_INFO
                            - MODIFY_AND_RETRY
                            - RETRY
                            - SKIP_STEP
                            - ABORT_PLAN

                            Classification rules:
                            - Use ABORT_PLAN only if the user wants to stop the whole workflow.
                            - Use SKIP_STEP only if the user wants to skip the current blocked step.
                            - Use RETRY only if the user wants to rerun the same step without changing parameters.
                            - Use MODIFY_AND_RETRY if the user provides replacement values or asks to retry with changed parameters.
                            - Otherwise use PROVIDE_INFO.
                            """)
                    .user("""
                            Objective:
                            %s

                            Blocked step index:
                            %s

                            Blocked step:
                            %s

                            Issue:
                            %s

                            Suggested action:
                            %s

                            User reply:
                            %s
                            """.formatted(
                            pendingFeedback.getObjective(),
                            pendingFeedback.getStepIndex() + 1,
                            pendingFeedback.getFailedStep().getDescription(),
                            pendingFeedback.getErrorMessage(),
                            pendingFeedback.getSuggestedAction(),
                            userInput
                    ))
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode jsonNode = objectMapper.readTree(normalized);
            JsonNode actionNode = jsonNode.get("action");
            if (actionNode == null || actionNode.asText().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(HumanFeedbackResponse.ActionType.valueOf(
                    actionNode.asText().trim().toUpperCase(Locale.ROOT)
            ));
        } catch (Exception ex) {
            log.warn("Failed to infer workflow feedback action with LLM", ex);
            return Optional.empty();
        }
    }
}
