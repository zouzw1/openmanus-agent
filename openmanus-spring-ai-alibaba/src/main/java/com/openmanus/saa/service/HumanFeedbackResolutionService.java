package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
import java.util.ArrayList;
import java.util.List;
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

        HumanFeedbackResponse baseResponse = HumanFeedbackResolver.buildResponse(finalAction, rawInput, request);
        InferencePolicy inferencePolicy = inferInferencePolicy(rawInput, pendingFeedback, finalAction).orElse(null);
        ReplanDecision replanDecision = inferReplanDecision(rawInput, pendingFeedback, finalAction)
                .orElseGet(ReplanDecision::none);
        return new HumanFeedbackResponse(
                baseResponse.getAction(),
                baseResponse.getProvidedInfo(),
                baseResponse.getModifiedParams(),
                inferencePolicy,
                replanDecision.replanRequired(),
                replanDecision.updatedObjective()
        );
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

    private Optional<InferencePolicy> inferInferencePolicy(
            String userInput,
            HumanFeedbackRequest pendingFeedback,
            HumanFeedbackResponse.ActionType action
    ) {
        if (action != HumanFeedbackResponse.ActionType.PROVIDE_INFO
                && action != HumanFeedbackResponse.ActionType.MODIFY_AND_RETRY) {
            return Optional.empty();
        }

        try {
            String content = chatClient.prompt()
                    .system("""
                            You convert a user's natural-language feedback on a paused workflow into a structured inference policy.

                            Return JSON only in this exact schema:
                            {
                              "inferenceAllowed": true,
                              "inferenceScope": "non_critical_missing_fields",
                              "providedFacts": ["departure_city=Shanghai"],
                              "delegatedFields": ["budget", "interest_preferences"],
                              "mustConfirmFields": ["travel_dates"],
                              "rationale": "short explanation"
                            }

                            Rules:
                            - `inferenceAllowed` is true only when the user delegates unspecified decisions or relaxes constraints.
                            - Use `inferenceScope=none` when no delegation is present.
                            - `providedFacts` should contain only facts explicitly stated by the user.
                            - `delegatedFields` should contain fields the assistant may fill in based on the user's delegation.
                            - `mustConfirmFields` should contain fields that still require explicit user confirmation.
                            - For travel planning, dates are confirm-required only when the user has not delegated trip duration/timing and exact timing materially changes the plan.
                            - Prefer conservative interpretations, but do not force confirmation for low-risk planning defaults when the user has clearly delegated choices.
                            """)
                    .user("""
                            Objective:
                            %s

                            Blocked step:
                            %s

                            Current issue:
                            %s

                            User reply:
                            %s
                            """.formatted(
                            pendingFeedback.getObjective(),
                            pendingFeedback.getFailedStep().getDescription(),
                            pendingFeedback.getErrorMessage(),
                            userInput
                    ))
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);

            boolean inferenceAllowed = root.path("inferenceAllowed").asBoolean(false);
            String inferenceScope = root.path("inferenceScope").asText("none");
            List<String> providedFacts = readStringArray(root.path("providedFacts"));
            List<String> delegatedFields = readStringArray(root.path("delegatedFields"));
            List<String> mustConfirmFields = readStringArray(root.path("mustConfirmFields"));
            String rationale = root.path("rationale").asText("");

            return Optional.of(new InferencePolicy(
                    inferenceAllowed,
                    inferenceScope,
                    providedFacts,
                    delegatedFields,
                    mustConfirmFields,
                    rationale
            ));
        } catch (Exception ex) {
            log.warn("Failed to infer inference policy with LLM", ex);
            return Optional.empty();
        }
    }

    private Optional<ReplanDecision> inferReplanDecision(
            String userInput,
            HumanFeedbackRequest pendingFeedback,
            HumanFeedbackResponse.ActionType action
    ) {
        if (action == HumanFeedbackResponse.ActionType.ABORT_PLAN
                || action == HumanFeedbackResponse.ActionType.SKIP_STEP
                || action == HumanFeedbackResponse.ActionType.RETRY) {
            return Optional.empty();
        }

        try {
            String content = chatClient.prompt()
                    .system("""
                            You decide whether a user's reply to a paused workflow should trigger replanning of the remaining workflow.

                            Return JSON only in this exact schema:
                            {
                              "replanRequired": true,
                              "updatedObjective": "standalone updated objective",
                              "rationale": "short explanation"
                            }

                            Rules:
                            - Set `replanRequired=true` if the user changes the requested deliverable, output format, artifact type, or overall remaining approach.
                            - Set `replanRequired=false` if the user only provides missing values, clarifies the blocked step, or asks to retry the same step.
                            - When replanning is required, `updatedObjective` must be a standalone objective string that preserves the original objective except for the user's latest override.
                            - The user's latest instruction overrides conflicting earlier requirements.
                            - If replanning is not required, return an empty string for `updatedObjective`.
                            """)
                    .user("""
                            Original objective:
                            %s

                            Blocked step:
                            %s

                            Current issue:
                            %s

                            User reply:
                            %s
                            """.formatted(
                            pendingFeedback.getObjective(),
                            pendingFeedback.getFailedStep().getDescription(),
                            pendingFeedback.getErrorMessage(),
                            userInput
                    ))
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);
            boolean replanRequired = root.path("replanRequired").asBoolean(false);
            String updatedObjective = root.path("updatedObjective").asText("").trim();

            if (!replanRequired) {
                return Optional.of(ReplanDecision.none());
            }

            if (updatedObjective.isBlank()) {
                updatedObjective = pendingFeedback.getObjective() + "\n\nLatest user instruction to apply when replanning: "
                        + userInput;
            }

            return Optional.of(new ReplanDecision(true, updatedObjective));
        } catch (Exception ex) {
            log.warn("Failed to infer replanning decision with LLM", ex);
            return Optional.empty();
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private record ReplanDecision(
            boolean replanRequired,
            String updatedObjective
    ) {
        private static ReplanDecision none() {
            return new ReplanDecision(false, null);
        }
    }
}
