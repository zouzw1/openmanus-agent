package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentCapabilitySnapshot;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.SkillCapabilityDescriptor;
import com.openmanus.saa.model.SkillInfoResponse;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.service.mcp.McpService;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.util.IntentResolutionHelper;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final ToolRegistryService toolRegistryService;
    private final McpPromptContextService mcpPromptContextService;
    private final McpService mcpService;
    private final SkillsService skillsService;
    private final SkillCapabilityService skillCapabilityService;
    private final ObjectMapper objectMapper;

    public PlanningService(
            ChatClient chatClient,
            OpenManusProperties properties,
            ToolRegistryService toolRegistryService,
            McpPromptContextService mcpPromptContextService,
            McpService mcpService,
            SkillsService skillsService,
            SkillCapabilityService skillCapabilityService
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.toolRegistryService = toolRegistryService;
        this.mcpPromptContextService = mcpPromptContextService;
        this.mcpService = mcpService;
        this.skillsService = skillsService;
        this.skillCapabilityService = skillCapabilityService;
        this.objectMapper = new ObjectMapper();
    }

    public PlanResponse createPlan(String objective) {
        String languageDirective = ResponseLanguageHelper.responseDirective(objective);
        String content = chatClient.prompt()
                .system("""
                        You are an expert Planning Agent tasked with solving problems efficiently through structured plans.
                        Your job is:
                        1. Analyze requests to understand the task scope
                        2. Create a clear, actionable plan that makes meaningful progress
                        3. Break tasks into logical steps with clear outcomes (3-6 steps)
                        4. Consider dependencies and verification methods
                        5. Know when to conclude - don't continue thinking once objectives are met

                        IMPORTANT PLANNING RULES:
                        - Each step must be an action, not internal reasoning.
                        - Do not include explanations, analysis, or long narratives inside steps.
                        - Do not invent pseudo-parameter names, slot names, JSON keys, or schema fields.
                        - If some user information is missing, describe it in plain language, for example:
                          "ask the user for departure city"
                          not:
                          "collect parameter departureCity"
                        - Mention tool names only when they are clear and necessary.
                        - If the exact tool schema is unknown, keep the step in natural language.

                        Return plain text only, one step per line, without numbering prefixes.
                        Each step should be concise and actionable.

                        %s
                        """.formatted(languageDirective))
                .user("""
                        Task: %s

                        Return the plan only.
                        Do not include reasoning.
                        """.formatted(objective))
                .call()
                .content();

        List<String> steps = Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripPrefix)
                .collect(Collectors.toList());
        return new PlanResponse(objective, steps, buildHumanFriendlyPlanSummary(objective, steps));
    }

    public List<WorkflowStep> createWorkflowPlan(String objective, List<AgentCapabilitySnapshot> agentSnapshots) {
        return createWorkflowPlan(objective, agentSnapshots, null);
    }

    public List<WorkflowStep> createWorkflowPlan(
            String objective,
            List<AgentCapabilitySnapshot> agentSnapshots,
            IntentResolution intentResolution
    ) {
        return createWorkflowPlan(objective, agentSnapshots, intentResolution, null);
    }

    /**
     * 创建工作流计划（带对话上下文）。
     */
    public List<WorkflowStep> createWorkflowPlan(
            String objective,
            List<AgentCapabilitySnapshot> agentSnapshots,
            IntentResolution intentResolution,
            ConversationContext context
    ) {
        validateObjectiveCapabilitySupportOrThrow(objective, agentSnapshots);
        DraftPlan draftPlan = createWorkflowDraft(objective, agentSnapshots, intentResolution, context);
        draftPlan.lintWarnings().forEach(warning -> log.warn("Workflow draft lint: {}", warning));
        return compileWorkflowPlan(objective, draftPlan.steps(), agentSnapshots);
    }

    private DraftPlan createWorkflowDraft(
            String objective,
            List<AgentCapabilitySnapshot> agentSnapshots,
            IntentResolution intentResolution
    ) {
        return createWorkflowDraft(objective, agentSnapshots, intentResolution, null);
    }

    private DraftPlan createWorkflowDraft(
            String objective,
            List<AgentCapabilitySnapshot> agentSnapshots,
            IntentResolution intentResolution,
            ConversationContext context
    ) {
        String toolsSchema = buildPlanningToolGuidance();
        String languageDirective = ResponseLanguageHelper.responseDirective(objective);
        String availableAgents = buildAgentCapabilityPrompt(agentSnapshots);
        Set<String> allowedAgents = agentSnapshots.stream()
                .map(AgentCapabilitySnapshot::agentId)
                .filter(agentId -> agentId != null && !agentId.isBlank())
                .collect(Collectors.toSet());
        String intentContext = buildIntentPlanningContext(intentResolution);
        String conversationContext = buildConversationContextPrompt(context);

        String content = chatClient.prompt()
                .system("""
                        You are an expert Planning Agent tasked with solving problems efficiently through structured plans.

                        Your job is:
                        1. Create a clear, actionable workflow plan with assigned agents.
                        2. Break tasks into 2-5 concrete, non-overlapping steps.
                        3. For each step, assign exactly one agent from available agents.
                        4. Make the first step directly executable.
                        5. Avoid duplicate or near-duplicate steps.
                        6. Do not include reasoning, summaries, or commentary as steps.

                        Available agents:
                        %s

                        Intent context:
                        %s

                        %s

                        AVAILABLE TOOLS AND THEIR SCHEMAS:
                        %s

                        IMPORTANT PLANNING RULES:
                        1. Each step must be one executable action.
                        2. The "agent" field MUST be one of the available agents, never a tool name.
                        3. CRITICAL: Before assigning an agent to a step, verify that the agent has the required tools. Check the localTools list for each agent.
                        4. Never assign an agent to a step if that agent does not have the tools needed for that step.
                        5. Never output meta steps such as "we need to..." or "the first step is...".
                        6. Never restate the task as a step.
                        7. If a required parameter cannot be obtained, create at most one explicit clarification step.
                        8. Do not repeat the same tool call for the same target unless the task explicitly requires it.
                        9. Do not invent pseudo-parameter names, slot names, aliases, or fake schema fields inside step descriptions.
                        10. If information is missing, describe the missing information in plain language.
                        11. When the task explicitly asks for a formatted Word, PDF, PPTX, or similar deliverable and an appropriate skill is available, prefer a step that uses that skill instead of a plain text file write.
                        12. Separate collection, composition, and export work into different steps when the final deliverable requires transformation.
                         13. Before any export/render/convert step for a non-text deliverable, include an earlier step that composes the source content or writes a draft artifact.
                         14. Do not use export/render skills such as docx/pdf/pptx to invent the underlying content plan; use a drafting/composition step first.
                         15. Only add a final export or file-writing delivery step when the user explicitly requests a file/document/export/save-to-workspace result or a specific output format.
                         16. Only reference tools, MCP tools, and skills that are explicitly available in the capability list and tool guidance above.
                         17. Never propose a fake office deliverable such as writing plain text into a .docx/.pdf/.pptx file when the required export capability is unavailable.

                         Return JSON only as an array.
                         Each item must follow this schema:
                        {
                          "agent": "manus",
                          "description": "use the appropriate tool to retrieve the required data"
                        }

                        %s
                        """.formatted(availableAgents, intentContext, conversationContext, toolsSchema, languageDirective))
                .user("""
                        Task: %s

                        Output 2-5 executable steps only.
                        Do not include reasoning.
                        """.formatted(objective))
                .call()
                .content();

        List<WorkflowStep> parsedSteps = parseWorkflowPlan(content, allowedAgents);
        List<WorkflowStep> sanitizedSteps = sanitizeWorkflowSteps(parsedSteps, allowedAgents);
        return new DraftPlan(sanitizedSteps, lintWorkflowDraft(parsedSteps, sanitizedSteps));
    }

    /**
     * 构建对话上下文提示。
     */
    private String buildConversationContextPrompt(ConversationContext context) {
        if (context == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 对话历史
        if (context.conversationHistory() != null && !context.conversationHistory().isBlank()) {
            sb.append("CONVERSATION HISTORY:\n")
              .append(context.conversationHistory())
              .append("\n\n");
        }

        // 用户偏好
        if (context.hasUserPreferences()) {
            sb.append("USER PREFERENCES:\n");
            context.userPreferences().forEach((key, value) ->
                sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildIntentPlanningContext(IntentResolution intentResolution) {
        if (intentResolution == null) {
            return "- No explicit intent context was provided. Use only the user objective.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("- intentId: ").append(intentResolution.intentId()).append("\n")
                .append("- routeMode: ").append(intentResolution.routeMode()).append("\n")
                .append("- preferredAgentId: ").append(intentResolution.preferredAgentId() == null ? "none" : intentResolution.preferredAgentId()).append("\n");

        // 输出期望
        boolean needsFile = IntentResolutionHelper.needsFile(intentResolution);
        String userFormat = IntentResolutionHelper.getUserSpecifiedFormat(intentResolution);
        String reason = IntentResolutionHelper.getReason(intentResolution);
        builder.append("- outputExpectation:\n");
        builder.append("  - needsFile: ").append(needsFile).append("\n");
        if (userFormat != null) {
            builder.append("  - userSpecifiedFormat: ").append(userFormat).append("\n");
        }
        if (reason != null) {
            builder.append("  - reason: ").append(reason).append("\n");
        }

        // 输出指导
        builder.append("- outputGuidance: ");
        if (needsFile) {
            builder.append("The user expects the result as a file deliverable. ");
            if (userFormat != null) {
                builder.append("Format specified: ").append(userFormat).append(". ");
            }
            builder.append("Ensure the workflow includes a final step to compose and export the deliverable.\n");
        } else {
            builder.append("The user expects the result as text/markdown output, not a file. ");
            builder.append("CRITICAL: After data collection steps, you MUST include a final 'compose' step that integrates ALL collected information into the user's requested deliverable. ");
            builder.append("Assign this compose step to the 'manus' agent which has composition capabilities. ");
            builder.append("Do NOT just collect data - the workflow must end with a step that generates the actual plan/itinerary/report the user asked for.\n");
        }

        builder.append("\nWORKFLOW STRUCTURE REQUIREMENT:\n");
        builder.append("- For any task that requires generating a plan, report, or recommendation: include both 'collection' steps AND a final 'compose' step.\n");
        builder.append("- Collection steps: gather data using specialized tools (attractions, weather, budget, etc.)\n");
        builder.append("- Compose step: integrate collected data into the final deliverable. This step should be assigned to 'manus' agent.\n");
        builder.append("- Example: For a travel plan request -> Step 1: search attractions, Step 2: check weather, Step 3: COMPOSE the detailed itinerary (manus agent)\n");

        if (intentResolution.attributes() != null && !intentResolution.attributes().isEmpty()) {
            // 过滤掉已处理的 outputExpectation
            Map<String, Object> otherAttributes = new LinkedHashMap<>(intentResolution.attributes());
            otherAttributes.remove("outputExpectation");
            otherAttributes.remove("responseMode");
            if (!otherAttributes.isEmpty()) {
                builder.append("- otherAttributes: ").append(otherAttributes).append("\n");
            }
        }
        if (intentResolution.planningHints() != null && !intentResolution.planningHints().isEmpty()) {
            builder.append("- planningHints:\n");
            for (String planningHint : intentResolution.planningHints()) {
                builder.append("  - ").append(planningHint).append("\n");
            }
        } else {
            builder.append("- planningHints: none\n");
        }
        builder.append("- Treat preferredAgentId and planningHints as strong routing hints when they match the available capabilities.");
        builder.append("- Follow outputGuidance strictly to determine whether to produce file artifacts or text output.");
        return builder.toString().trim();
    }

    private List<WorkflowStep> compileWorkflowPlan(
            String objective,
            List<WorkflowStep> draftSteps,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        List<WorkflowStep> draftSafeSteps = draftSteps == null ? List.of() : draftSteps;
        List<WorkflowStep> enrichedSteps = enrichStepsWithToolMetadata(draftSafeSteps, objective);
        List<WorkflowStep> deduplicatedSteps = removeDuplicateWorkflowSteps(enrichedSteps);
        List<WorkflowStep> normalizedSteps = enforceWorkflowStructure(objective, deduplicatedSteps, agentSnapshots);
        removeRedundantOfficeDeliveryWrites(normalizedSteps);
        validateWorkflowPlanOrThrow(objective, normalizedSteps, agentSnapshots);
        return normalizedSteps;
    }

    private List<String> lintWorkflowDraft(List<WorkflowStep> parsedSteps, List<WorkflowStep> sanitizedSteps) {
        List<String> warnings = new ArrayList<>();
        if (parsedSteps == null || parsedSteps.isEmpty()) {
            warnings.add("Draft planning returned no executable steps before sanitization.");
            return warnings;
        }
        if (sanitizedSteps == null || sanitizedSteps.isEmpty()) {
            warnings.add("Draft planning produced only meta or empty steps after sanitization.");
            return warnings;
        }
        if (sanitizedSteps.size() < parsedSteps.size()) {
            warnings.add("Draft planning included duplicate or meta steps that were removed during sanitization.");
        }
        return warnings;
    }

    private void validateWorkflowPlanOrThrow(
            String objective,
            List<WorkflowStep> workflowSteps,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        OpenManusProperties.PlanningValidationProperties validation = properties.getPlanningValidation();
        if (validation == null || !validation.isEnabled()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        Map<String, AgentCapabilitySnapshot> snapshotsByAgent = agentSnapshots.stream()
                .collect(Collectors.toMap(
                        AgentCapabilitySnapshot::agentId,
                        snapshot -> snapshot,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        String requestedDeliverableFormat = inferRequestedDeliverableFormat(objective);

        if (validation.isValidateRequestedDeliverable()
                && requestedDeliverableFormat != null
                && !requestedDeliverableFormat.isBlank()) {
            boolean environmentSupportsDeliverable = environmentSupportsDeliverable(requestedDeliverableFormat, agentSnapshots);
            boolean planContainsDeliverableProducer = workflowSteps.stream()
                    .anyMatch(step -> stepCanProduceDeliverable(step, requestedDeliverableFormat));

            if (!environmentSupportsDeliverable) {
                errors.add(buildUnsupportedDeliverableMessage(objective, requestedDeliverableFormat, agentSnapshots));
            } else if (!planContainsDeliverableProducer) {
                errors.add("The objective requests a ." + requestedDeliverableFormat
                        + " deliverable, but the generated workflow contains no step with a skill that can produce this format.");
            }
        }

        for (int i = 0; i < workflowSteps.size(); i++) {
            WorkflowStep step = workflowSteps.get(i);
            AgentCapabilitySnapshot snapshot = snapshotsByAgent.get(step.agent());
            if (validation.isValidateAgentCapabilities() && snapshot == null) {
                errors.add("Step " + (i + 1) + " is assigned to unknown or non-planning agent '" + step.agent() + "'.");
                continue;
            }
            if (snapshot == null) {
                continue;
            }
            String skillName = declaredSkillName(step);
            List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
            for (String requiredTool : requiredTools) {
                if ("read_skill".equals(requiredTool)) {
                    if (validation.isValidateAgentCapabilities() && !snapshot.supportsReadSkill()) {
                        errors.add("Step " + (i + 1) + " requires read_skill, but agent '" + snapshot.agentId() + "' has no allowed skills.");
                    }
                    continue;
                }
                if (validation.isValidateAgentCapabilities() && !snapshot.hasLocalTool(requiredTool)) {
                    errors.add("Step " + (i + 1) + " requires local tool '" + requiredTool
                            + "', but agent '" + snapshot.agentId() + "' does not allow it.");
                    continue;
                }
                if (validation.isValidateLocalToolParameters() && shouldValidateLocalToolParameters(step, requiredTool)) {
                    validatePlannedLocalToolParameters(errors, i, step, requiredTool);
                }
            }
            if (validation.isValidateAgentCapabilities() && requiredTools.contains("read_skill") && skillName == null) {
                errors.add("Step " + (i + 1) + " requires read_skill but does not declare parameterContext.skillName.");
            }
            if (validation.isValidateAgentCapabilities() && skillName != null && !snapshot.hasSkill(skillName)) {
                errors.add("Step " + (i + 1) + " declares skill '" + skillName
                        + "', but agent '" + snapshot.agentId() + "' does not allow it.");
            }

            if (validation.isValidateAgentCapabilities()
                    && requiredTools.contains("createPlan")
                    && skillName != null
                    && !"project-planning".equals(skillName)) {
                errors.add("Step " + (i + 1) + " uses createPlan but is bound to skill '" + skillName
                        + "'. Steps using createPlan must use project-planning or no skill.");
            }

            if (skillName == null) {
                validatePlannedMcpInvocation(errors, i, step, snapshot);
                continue;
            }

            String targetFormat = inferStepTargetFormat(step);
            if (targetFormat == null) {
                continue;
            }

            Set<String> supportedOutputs = supportedOutputFormatsForSkill(skillName);
            if (validation.isValidateSkillOutputFormats()
                    && !supportedOutputs.isEmpty()
                    && !supportedOutputs.contains(targetFormat)) {
                errors.add("Step " + (i + 1) + " uses skill '" + skillName + "' for a ." + targetFormat
                        + " output, but this skill only supports: " + String.join(", ", supportedOutputs));
            }

            validatePlannedMcpInvocation(errors, i, step, snapshot);
        }

        validateWorkflowStructure(errors, workflowSteps);

        if (!errors.isEmpty()) {
            if (validation.isFailOnErrors()) {
                throw new PlanValidationException(String.join(" ", errors), workflowSteps);
            }
            errors.forEach(error -> log.warn("Workflow plan validation warning: {}", error));
        }
    }

    private List<WorkflowStep> enrichStepsWithToolMetadata(List<WorkflowStep> steps, String objective) {
        String toolsSchema = buildPlanningToolGuidance();
        return steps.stream().map(step -> {
            try {
                String extractionPrompt = """
                        Analyze this workflow step and extract the required tools and parameters.

                        Available tools guidance:
                        %s

                        Step description: %s
                        Original objective: %s

                        IMPORTANT - Tool Extraction Rules:
                        1. Only extract tools that are DIRECTLY needed to execute THIS specific step.
                        2. Do NOT include tools that were used in PREVIOUS steps (e.g., if step says "combine the results from step X", only include the combining tool, not the tools from step X).
                        3. Do NOT include tools that will be used in FUTURE steps.
                        4. For "combine", "merge", or "compose" steps, typically only writeWorkspaceFile is needed.
                        5. For "convert to PDF" steps, use markdownToPdf or htmlToPdf.
                        6. Use ONLY exact tool names and exact parameter names from the provided schemas.
                        7. If a parameter name is not explicitly defined in the tool schema, do not invent an alias.

                        Return ONLY a JSON object in this exact format:
                        {
                          "requiredTools": ["tool1", "tool2"],
                          "parameters": {
                            "param1": "value1",
                            "param2": "value2"
                          }
                        }

                        If no tools are needed, use empty array: []
                        If no parameters can be extracted, use empty object: {}
                        Do NOT include markdown formatting or explanations.
                        """.formatted(toolsSchema, step.description(), objective);

                String jsonResponse = chatClient.prompt()
                        .user(extractionPrompt)
                        .call()
                        .content();

                JsonNode jsonNode = objectMapper.readTree(stripMarkdownCodeFence(jsonResponse));

                Set<String> requiredTools = new LinkedHashSet<>();
                if (jsonNode.has("requiredTools") && jsonNode.get("requiredTools").isArray()) {
                    for (JsonNode tool : jsonNode.get("requiredTools")) {
                        String toolName = tool.asText();
                        if (isPlanningKnownTool(toolName)) {
                            requiredTools.add(toolName);
                        }
                    }
                }

                Map<String, Object> parameters = new HashMap<>();
                if (jsonNode.has("parameters") && jsonNode.get("parameters").isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.get("parameters").fields();
                    Set<String> allowedParameterNames = resolveAllowedParameterNames(requiredTools);
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        if (!allowedParameterNames.isEmpty() && allowedParameterNames.contains(entry.getKey())) {
                            parameters.put(entry.getKey(), entry.getValue().asText());
                        }
                    }
                }

                normalizeMisclassifiedLocalToolInvocation(requiredTools, parameters);
                enrichSkillMetadata(step.description(), objective, requiredTools, parameters);
                normalizeSkillExecutionPlan(step.description(), objective, requiredTools, parameters);

                log.info("Enriched step: {} -> tools={}, params={}", step.description(), requiredTools, parameters);
                return new WorkflowStep(step.agent(), step.description(), List.copyOf(requiredTools), parameters);
            } catch (Exception e) {
                log.warn("Failed to extract tool metadata for step: {}, using defaults", step.description(), e);
                return new WorkflowStep(step.agent(), step.description());
            }
        }).collect(Collectors.toList());
    }

    private List<WorkflowStep> parseWorkflowPlan(String content, Set<String> allowedAgents) {
        String normalizedContent = stripMarkdownCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(normalizedContent);
            if (root.isArray()) {
                List<WorkflowStep> steps = new ArrayList<>();
                for (JsonNode node : root) {
                    String agent = sanitizeAgentName(node.path("agent").asText("manus").trim(), allowedAgents);
                    String description = node.path("description").asText("").trim();
                    if (!description.isBlank()) {
                        steps.add(new WorkflowStep(agent.isBlank() ? "manus" : agent, description));
                    }
                }
                if (!steps.isEmpty()) {
                    return steps;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse workflow plan as JSON, falling back to line parser", e);
        }

        return Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripPrefix)
                .map(this::toWorkflowStep)
                .map(step -> new WorkflowStep(sanitizeAgentName(step.agent(), allowedAgents), step.description()))
                .collect(Collectors.toList());
    }

    private List<WorkflowStep> sanitizeWorkflowSteps(List<WorkflowStep> steps, Set<String> allowedAgents) {
        List<WorkflowStep> sanitized = new ArrayList<>();
        for (WorkflowStep step : steps) {
            String description = step.description().trim();
            if (description.isBlank() || isMetaPlanningStep(description)) {
                continue;
            }

            boolean duplicateDescription = sanitized.stream()
                    .map(existing -> normalizeDescription(existing.description()))
                    .anyMatch(normalized -> normalized.equals(normalizeDescription(description)));
            if (!duplicateDescription) {
                sanitized.add(new WorkflowStep(sanitizeAgentName(step.agent(), allowedAgents), description));
            }
        }
        return sanitized.isEmpty() ? steps : sanitized;
    }

    private boolean isMetaPlanningStep(String description) {
        String normalized = normalizeDescription(description);
        return normalized.contains("thetaskis")
                || normalized.contains("weneedto")
                || normalized.contains("needtoquery")
                || normalized.contains("thefirststepis")
                || normalized.contains("\u5f53\u524d\u6ca1\u6709")
                || normalized.contains("\u56e0\u6b64\u7b2c\u4e00\u6b65")
                || normalized.contains("\u7b2c\u4e00\u6b65\u662f");
    }

    private List<WorkflowStep> removeDuplicateWorkflowSteps(List<WorkflowStep> steps) {
        List<WorkflowStep> deduplicated = new ArrayList<>();
        for (WorkflowStep candidate : steps) {
            boolean duplicate = deduplicated.stream().anyMatch(existing -> areDuplicateWorkflowSteps(existing, candidate));
            if (!duplicate) {
                deduplicated.add(candidate);
            }
        }
        return deduplicated.isEmpty() ? steps : deduplicated;
    }

    private List<WorkflowStep> enforceWorkflowStructure(
            String objective,
            List<WorkflowStep> workflowSteps,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        if (workflowSteps == null || workflowSteps.isEmpty()) {
            return List.of();
        }

        List<WorkflowStep> normalizedSteps = new ArrayList<>();
        boolean hasDraftProducer = false;
        for (WorkflowStep step : workflowSteps) {
            if (isDraftProducerStep(step)) {
                hasDraftProducer = true;
            }
            if (isExportTransformStep(step) && !hasDraftProducer) {
                WorkflowStep draftStep = buildDraftPreparationStep(objective, step, agentSnapshots);
                if (draftStep != null) {
                    normalizedSteps.add(draftStep);
                    hasDraftProducer = true;
                }
            }
            normalizedSteps.add(step);
            if (isDraftProducerStep(step)) {
                hasDraftProducer = true;
            }
        }
        trimUnrequestedTerminalDeliverySteps(objective, normalizedSteps);
        return normalizedSteps;
    }

    private void validateWorkflowStructure(List<String> errors, List<WorkflowStep> workflowSteps) {
        boolean hasDraftProducer = false;
        for (int i = 0; i < workflowSteps.size(); i++) {
            WorkflowStep step = workflowSteps.get(i);
            if (isDraftProducerStep(step)) {
                hasDraftProducer = true;
            }
            if (isExportTransformStep(step) && !hasDraftProducer) {
                errors.add("Step " + (i + 1)
                        + " is an export/transform step for a non-text deliverable but no earlier step composes source content or writes a draft artifact.");
            }
        }
    }

    private boolean areDuplicateWorkflowSteps(WorkflowStep left, WorkflowStep right) {
        if (normalizeDescription(left.description()).equals(normalizeDescription(right.description()))) {
            return true;
        }

        String leftTool = normalizeValue(left.primaryTool());
        String rightTool = normalizeValue(right.primaryTool());
        if (leftTool.isBlank() || !leftTool.equals(rightTool)) {
            return false;
        }

        Map<String, String> leftParams = canonicalizeParameters(left.parameterContext());
        Map<String, String> rightParams = canonicalizeParameters(right.parameterContext());
        if (leftParams.equals(rightParams)) {
            return true;
        }

        String leftTarget = firstNonBlank(leftParams.get("location"), leftParams.get("url"), leftParams.get("path"), leftParams.get("query"));
        String rightTarget = firstNonBlank(rightParams.get("location"), rightParams.get("url"), rightParams.get("path"), rightParams.get("query"));
        if (leftTarget == null || !leftTarget.equals(rightTarget)) {
            return false;
        }

        return noConflictingSharedParameters(leftParams, rightParams);
    }

    private boolean noConflictingSharedParameters(Map<String, String> leftParams, Map<String, String> rightParams) {
        for (Map.Entry<String, String> entry : leftParams.entrySet()) {
            String rightValue = rightParams.get(entry.getKey());
            if (rightValue != null && !Objects.equals(entry.getValue(), rightValue)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> canonicalizeParameters(Map<String, Object> parameterContext) {
        Map<String, String> canonical = new HashMap<>();
        if (parameterContext == null) {
            return canonical;
        }

        for (Map.Entry<String, Object> entry : parameterContext.entrySet()) {
            String key = switch (entry.getKey()) {
                case "city" -> "location";
                case "date", "time_period" -> "time";
                default -> entry.getKey();
            };
            String value = normalizeValue(String.valueOf(entry.getValue()));
            if (!value.isBlank()) {
                canonical.put(key, value);
            }
        }
        return canonical;
    }

    private String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("```json", "").replace("```", "").trim();
    }

    private String stripPrefix(String line) {
        return line.replaceFirst("^[-*\\d.\\s]+", "").trim();
    }

    private Set<String> parseAllowedAgents(String availableAgents) {
        return Arrays.stream(availableAgents.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2))
                .map(line -> {
                    int idx = line.indexOf(':');
                    return idx >= 0 ? line.substring(0, idx).trim() : line.trim();
                })
                .filter(line -> !line.isBlank())
                .collect(Collectors.toSet());
    }

    private String sanitizeAgentName(String rawAgent, Set<String> allowedAgents) {
        if (rawAgent == null || rawAgent.isBlank()) {
            return "manus";
        }
        return allowedAgents.contains(rawAgent) ? rawAgent : "manus";
    }

    private Set<String> resolveAllowedParameterNames(java.util.Collection<String> requiredTools) {
        Set<String> names = requiredTools.stream()
                .map(toolRegistryService::getTool)
                .flatMap(Optional::stream)
                .flatMap(tool -> tool.getParameters().stream())
                .map(param -> param.getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requiredTools.contains("read_skill")) {
            names.add("skillName");
        }
        return names;
    }

    private String normalizeDescription(String description) {
        return description == null
                ? ""
                : description.toLowerCase()
                        .replaceAll("[^\\p{L}\\p{N}]+", "")
                        .trim();
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private WorkflowStep toWorkflowStep(String line) {
        if (line.startsWith("[") && line.contains("]")) {
            int end = line.indexOf(']');
            String agent = line.substring(1, end).trim();
            String description = line.substring(end + 1).trim();
            return new WorkflowStep(agent.isBlank() ? "manus" : agent, description);
        }
        return new WorkflowStep("manus", line);
    }

    private String buildHumanFriendlyPlanSummary(String objective, List<String> steps) {
        boolean chinese = ResponseLanguageHelper.detect(objective) == ResponseLanguageHelper.Language.ZH_CN;
        StringBuilder summary = new StringBuilder();

        if (chinese) {
            summary.append("## 计划摘要\n\n");
            summary.append("- 目标：").append(objective).append("\n");
            summary.append("- 计划步骤数：").append(steps.size()).append("\n\n");
            summary.append("## 审阅表\n\n");
        } else {
            summary.append("## Plan Summary\n\n");
            summary.append("- Objective: ").append(objective).append("\n");
            summary.append("- Number of steps: ").append(steps.size()).append("\n\n");
            summary.append("## Review Table\n\n");
        }

        if (steps.isEmpty()) {
            summary.append(chinese ? "暂无计划步骤。\n" : "No plan steps were generated.\n");
            return summary.toString().trim();
        }

        if (chinese) {
            summary.append("| 步骤 | 计划内容 | 用户审阅重点 |\n");
            summary.append("| --- | --- | --- |\n");
        } else {
            summary.append("| Step | Planned Action | What To Review |\n");
            summary.append("| --- | --- | --- |\n");
        }

        for (int i = 0; i < steps.size(); i++) {
            String reviewHint = chinese
                    ? "确认是否需要保留、修改、补充信息或调整顺序"
                    : "Check whether to keep, edit, add missing info, or reorder";
            summary.append("| ")
                    .append(i + 1)
                    .append(" | ")
                    .append(escapeMarkdownTableCell(steps.get(i)))
                    .append(" | ")
                    .append(reviewHint)
                    .append(" |\n");
        }

        return summary.toString().trim();
    }

    private String escapeMarkdownTableCell(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", "<br>");
    }

    private String buildPlanningToolGuidance() {
        return toolRegistryService.generateEnabledToolsPromptGuidance()
                + "\n\nTOOL SELECTION PRECEDENCE (CRITICAL):\n"
                + "1. If a tool name exists in LOCAL TOOLS list, ALWAYS use it directly in requiredTools.\n"
                + "2. NEVER wrap a local tool name inside callMcpTool.\n"
                + "3. Only use callMcpTool for tools that ONLY exist on MCP servers (not in local tools).\n"
                + "4. Local tools = Java implementation = faster, more reliable, no external dependencies.\n"
                + "\n\nPLANNING DISAMBIGUATION RULES:\n"
                + "- If a tool name exists in the local tool list, use that exact local tool name directly in requiredTools.\n"
                + "- Do NOT wrap a local tool inside callMcpTool.\n"
                + "- Use callMcpTool only for MCP tools explicitly listed in the available MCP tools section, with the exact serverId and toolName.\n"
                + "- If a capability name appears in the user request but it is not listed under available MCP tools, do not invent an MCP call for it.\n"
                + "\n\n"
                + buildSkillPlanningGuidance()
                + "\n\n"
                + mcpPromptContextService.describeAvailableTools();
    }

    private void normalizeMisclassifiedLocalToolInvocation(
            Set<String> requiredTools,
            Map<String, Object> parameters
    ) {
        if (requiredTools == null || parameters == null || !requiredTools.contains("callMcpTool")) {
            return;
        }

        Object toolNameValue = parameters.get("toolName");
        if (!(toolNameValue instanceof String toolName) || toolName.isBlank()) {
            return;
        }

        String normalizedToolName = toolName.trim();
        Optional<ToolMetadata> localTool = toolRegistryService.getTool(normalizedToolName);
        if (localTool.isEmpty()) {
            return;
        }

        // Always prefer local tool over MCP tool when a name collision exists.
        // Local tools are Java implementations with better reliability and no external dependencies.
        String serverId = Optional.ofNullable(parameters.get("serverId"))
                .map(Object::toString)
                .orElse("")
                .trim();
        boolean mcpToolExists = !serverId.isBlank() && mcpService.findToolMetadata(serverId, normalizedToolName).isPresent();
        if (mcpToolExists) {
            log.info("Tool name collision detected: '{}' exists as both LOCAL and MCP. Preferring LOCAL tool.",
                    normalizedToolName);
        }

        Map<String, Object> extractedArguments = parseToolArguments(parameters.get("argumentsJson"));
        Map<String, Object> normalizedParameters = new LinkedHashMap<>();
        for (ToolMetadata.ParameterSchema parameterSchema : localTool.get().getParameters()) {
            String parameterName = parameterSchema.getName();
            Object directValue = parameters.get(parameterName);
            if (directValue != null) {
                normalizedParameters.put(parameterName, directValue);
                continue;
            }
            Object argumentValue = extractedArguments.get(parameterName);
            if (argumentValue != null) {
                normalizedParameters.put(parameterName, argumentValue);
                continue;
            }
            String alias = resolveLocalToolParameterAlias(parameterName);
            if (alias != null && extractedArguments.get(alias) != null) {
                normalizedParameters.put(parameterName, extractedArguments.get(alias));
            }
        }

        parameters.clear();
        parameters.putAll(normalizedParameters);
        requiredTools.remove("callMcpTool");
        requiredTools.add(normalizedToolName);
        log.debug("Normalized misclassified tool invocation: callMcpTool({}) -> {}", toolName, normalizedToolName);
    }

    private Map<String, Object> parseToolArguments(Object argumentsJsonValue) {
        if (argumentsJsonValue == null) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(argumentsJsonValue));
            if (!root.isObject()) {
                return Map.of();
            }
            Map<String, Object> parsed = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    parsed.put(entry.getKey(), value.asText());
                } else if (value.isNumber()) {
                    parsed.put(entry.getKey(), value.numberValue());
                } else if (value.isBoolean()) {
                    parsed.put(entry.getKey(), value.asBoolean());
                } else {
                    parsed.put(entry.getKey(), value.toString());
                }
            }
            return parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String resolveLocalToolParameterAlias(String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return null;
        }
        return switch (parameterName) {
            case "relativePath" -> "filePath";
            default -> null;
        };
    }

    private String buildAgentCapabilityPrompt(List<AgentCapabilitySnapshot> agentSnapshots) {
        StringBuilder builder = new StringBuilder();
        for (AgentCapabilitySnapshot snapshot : agentSnapshots) {
            builder.append("- ")
                    .append(snapshot.agentId())
                    .append(": ")
                    .append(snapshot.description())
                    .append(" [executorType=")
                    .append(snapshot.executorType())
                    .append(", localTools=")
                    .append(snapshot.localTools().isEmpty() ? "none" : String.join(", ", snapshot.localTools()))
                    .append(", mcpTools=")
                    .append(snapshot.mcpTools().isEmpty() ? "none" : String.join(", ", snapshot.mcpTools()))
                    .append(", skills=")
                    .append(snapshot.skills().isEmpty() ? "none" : String.join(", ", snapshot.skills()))
                    .append("]\n");
        }
        return builder.toString().trim();
    }

    private void validatePlannedMcpInvocation(
            List<String> errors,
            int index,
            WorkflowStep step,
            AgentCapabilitySnapshot snapshot
    ) {
        OpenManusProperties.PlanningValidationProperties validation = properties.getPlanningValidation();
        List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
        if (!requiredTools.contains("callMcpTool") || step.parameterContext() == null) {
            return;
        }

        Object serverIdValue = step.parameterContext().get("serverId");
        Object toolNameValue = step.parameterContext().get("toolName");
        if (!(serverIdValue instanceof String serverId) || serverId.isBlank()
                || !(toolNameValue instanceof String toolName) || toolName.isBlank()) {
            if (validation.isValidateMcpCapabilities()) {
                errors.add("Step " + (index + 1) + " uses callMcpTool but does not declare parameterContext.serverId and parameterContext.toolName.");
            }
            return;
        }

        if (validation.isValidateMcpCapabilities() && !snapshot.hasMcpTool(serverId, toolName)) {
            errors.add("Step " + (index + 1) + " requires MCP tool '" + serverId + "/" + toolName
                    + "', but agent '" + snapshot.agentId() + "' cannot access it.");
            return;
        }

        Optional<McpToolMetadata> metadata = mcpService.findToolMetadata(serverId, toolName);
        if (!validation.isValidateMcpArguments() || metadata.isEmpty()) {
            return;
        }

        Object argumentsJsonValue = step.parameterContext().get("argumentsJson");
        String argumentsJson = argumentsJsonValue == null ? "" : argumentsJsonValue.toString();
        validatePlannedMcpArguments(errors, index, serverId, toolName, argumentsJson, metadata.get());
    }

    private void validatePlannedMcpArguments(
            List<String> errors,
            int stepIndex,
            String serverId,
            String toolName,
            String argumentsJson,
            McpToolMetadata metadata
    ) {
        if ((argumentsJson == null || argumentsJson.isBlank()) && metadata.requiredParameterNames().isEmpty()) {
            return;
        }
        JsonNode argumentsNode;
        try {
            String safeArgs = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            argumentsNode = objectMapper.readTree(safeArgs);
        } catch (Exception ex) {
            errors.add("Step " + (stepIndex + 1) + " uses MCP tool '" + serverId + "/" + toolName
                    + "' but parameterContext.argumentsJson is not valid JSON.");
            return;
        }
        if (!argumentsNode.isObject()) {
            errors.add("Step " + (stepIndex + 1) + " uses MCP tool '" + serverId + "/" + toolName
                    + "' but parameterContext.argumentsJson must be a JSON object.");
            return;
        }

        Set<String> allowedParameterNames = metadata.allParameterNames();
        Set<String> actualParameterNames = new LinkedHashSet<>();
        argumentsNode.fieldNames().forEachRemaining(actualParameterNames::add);
        if (!allowedParameterNames.isEmpty()) {
            Set<String> unsupportedParameters = new LinkedHashSet<>(actualParameterNames);
            unsupportedParameters.removeAll(allowedParameterNames);
            if (!unsupportedParameters.isEmpty()) {
                errors.add("Step " + (stepIndex + 1) + " uses MCP tool '" + serverId + "/" + toolName
                        + "' with unsupported arguments " + unsupportedParameters
                        + ". Allowed arguments are: " + allowedParameterNames + ".");
            }
        }

        Set<String> missingRequiredParameters = new LinkedHashSet<>(metadata.requiredParameterNames());
        missingRequiredParameters.removeAll(actualParameterNames);
        if (!missingRequiredParameters.isEmpty()) {
            errors.add("Step " + (stepIndex + 1) + " uses MCP tool '" + serverId + "/" + toolName
                    + "' but is missing required arguments " + missingRequiredParameters + ".");
        }
    }

    private void validatePlannedLocalToolParameters(
            List<String> errors,
            int stepIndex,
            WorkflowStep step,
            String toolName
    ) {
        if ("callMcpTool".equals(toolName) || "read_skill".equals(toolName)) {
            return;
        }

        Map<String, Object> parameters = step.parameterContext() == null ? Map.of() : step.parameterContext();
        List<String> missingRequiredParameters = toolRegistryService.getMissingRequiredParameters(toolName, parameters).stream()
                .filter(parameterName -> !isDeferredLocalToolParameter(toolName, parameterName))
                .toList();
        if (!missingRequiredParameters.isEmpty()) {
            errors.add("Step " + (stepIndex + 1) + " uses local tool '" + toolName
                    + "' but is missing required parameters " + missingRequiredParameters + ".");
        }
    }

    private boolean shouldValidateLocalToolParameters(WorkflowStep step, String toolName) {
        if ("callMcpTool".equals(toolName) || "read_skill".equals(toolName)) {
            return false;
        }
        if (step == null || step.parameterContext() == null || step.parameterContext().isEmpty()) {
            return false;
        }

        // Only validate local-tool parameters when the step already declares at least one
        // parameter belonging to that tool. Skill support tools are often added for execution
        // flexibility and should not be treated as explicit calls at planning time.
        return toolRegistryService.getTool(toolName)
                .map(tool -> tool.getParameters().stream()
                        .map(param -> param.getName())
                        .anyMatch(step.parameterContext()::containsKey))
                .orElse(false);
    }

    private boolean isDeferredLocalToolParameter(String toolName, String parameterName) {
        if (toolName == null || parameterName == null) {
            return false;
        }
        if ("writeWorkspaceFile".equals(toolName) && "content".equals(parameterName)) {
            return true;
        }
        return false;
    }

    private boolean isPlanningKnownTool(String toolName) {
        return "read_skill".equals(toolName) || toolRegistryService.getTool(toolName).isPresent();
    }

    private void enrichSkillMetadata(
            String stepDescription,
            String objective,
            Set<String> requiredTools,
            Map<String, Object> parameters
    ) {
        String existingSkillName = Optional.ofNullable(parameters.get("skillName"))
                .map(Object::toString)
                .orElse("")
                .trim();
        String normalizedExistingSkill = resolveActualSkillName(existingSkillName);
        if (normalizedExistingSkill != null
                && !isSkillCompatibleWithStep(normalizedExistingSkill, stepDescription, parameters)) {
            parameters.remove("skillName");
            normalizedExistingSkill = null;
        }
        String inferredSkillName = normalizedExistingSkill == null
                ? resolveSkillNameForStep(stepDescription, parameters)
                : normalizedExistingSkill;
        if (inferredSkillName == null) {
            return;
        }

        parameters.put("skillName", inferredSkillName);
        requiredTools.add("read_skill");
    }

    private String resolveActualSkillName(String skillName) {
        String normalized = normalizeSkillName(skillName);
        if (normalized == null) {
            return null;
        }
        return skillCapabilityService.resolveSkillName(normalized).orElse(normalized);
    }

    private boolean isSkillCompatibleWithStep(
            String skillName,
            String stepDescription,
            Map<String, Object> parameters
    ) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        if ("project-planning".equals(skillName)) {
            return true;
        }
        String targetFormat = inferTargetFormatForSkillResolution(stepDescription, parameters);
        if (targetFormat == null || targetFormat.isBlank()) {
            return true;
        }
        Set<String> supportedOutputs = supportedOutputFormatsForSkill(skillName);
        if (supportedOutputs.isEmpty()) {
            return true;
        }
        if (isTextDraftFormat(targetFormat)) {
            return supportedOutputs.contains(targetFormat);
        }
        return supportedOutputs.contains(targetFormat);
    }

    private String resolveSkillNameForStep(String stepDescription, Map<String, Object> parameters) {
        String targetFormat = inferTargetFormatForSkillResolution(stepDescription, parameters);
        String explicitSkill = skillCapabilityService.findMentionedSkillName(stepDescription)
                .map(this::normalizeSkillName)
                .orElse(null);
        if (explicitSkill != null
                && isSkillCompatibleWithStep(explicitSkill, stepDescription, parameters)
                && (explicitlyInvokesSkill(stepDescription, explicitSkill)
                || (targetFormat != null && !isTextDraftFormat(targetFormat)))) {
            return explicitSkill;
        }

        if (targetFormat == null || !looksLikeSkillManagedOutputStep(stepDescription, parameters, targetFormat)) {
            return null;
        }

        return skillCapabilityService.resolveUniqueSkillForOutputFormat(targetFormat)
                .map(this::normalizeSkillName)
                .orElse(null);
    }

    private boolean explicitlyInvokesSkill(String stepDescription, String skillName) {
        if (stepDescription == null || stepDescription.isBlank() || skillName == null || skillName.isBlank()) {
            return false;
        }
        String normalizedDescription = stepDescription.toLowerCase();
        String normalizedSkillName = skillName.toLowerCase();
        return normalizedDescription.contains(normalizedSkillName)
                && mentionsAny(normalizedDescription, "skill", "技能", "调用", "使用");
    }

    private String inferTargetFormatForSkillResolution(String stepDescription, Map<String, Object> parameters) {
        String formatFromDescription = inferFormatFromText(stepDescription);
        if (formatFromDescription != null && !formatFromDescription.isBlank() && !isTextDraftFormat(formatFromDescription)) {
            return formatFromDescription;
        }
        if (parameters != null) {
            Object relativePath = parameters.get("relativePath");
            if (relativePath instanceof String path && !path.isBlank()) {
                String formatFromPath = inferFormatFromPath(path);
                if (formatFromPath != null) {
                    return formatFromPath;
                }
            }
        }
        return formatFromDescription;
    }

    private boolean looksLikeSkillManagedOutputStep(
            String stepDescription,
            Map<String, Object> parameters,
            String targetFormat
    ) {
        if (targetFormat == null || "md".equals(targetFormat) || "txt".equals(targetFormat)) {
            return false;
        }
        String description = stepDescription == null ? "" : stepDescription.toLowerCase();
        if (mentionsAny(description, "export", "render", "convert", "format", "generate pdf", "generate docx", "generate pptx",
                ".pdf", ".docx", ".pptx", "pdf", "docx", "pptx")) {
            return true;
        }
        Object relativePathValue = parameters == null ? null : parameters.get("relativePath");
        return relativePathValue instanceof String path
                && targetFormat.equals(inferFormatFromPath(path));
    }

    private void normalizeSkillExecutionPlan(
            String stepDescription,
            String objective,
            Set<String> requiredTools,
            Map<String, Object> parameters
    ) {
        Object skillNameValue = parameters.get("skillName");
        if (!(skillNameValue instanceof String skillName) || skillName.isBlank()) {
            return;
        }

        if ("project-planning".equals(skillName)) {
            requiredTools.add("createPlan");

            Object relativePathValue = parameters.get("relativePath");
            if (relativePathValue instanceof String relativePath && !relativePath.isBlank()) {
                if (isOfficeDocumentPath(relativePath)) {
                    parameters.put("relativePath", convertToTextDraftPath(relativePath));
                }
                requiredTools.add("writeWorkspaceFile");
            } else if (mentionsPlanDraft(stepDescription, objective)) {
                parameters.put("relativePath", "output/plan_draft.md");
                requiredTools.add("writeWorkspaceFile");
            }
        }
    }

    private String inferSkillName(String text) {
        if (text == null || text.isBlank() || !skillsService.isEnabled()) {
            return null;
        }
        Optional<String> explicitSkill = skillCapabilityService.findMentionedSkillName(text);
        if (explicitSkill.isPresent()) {
            return explicitSkill.get();
        }
        String targetFormat = inferFormatFromText(text);
        if (targetFormat == null) {
            return null;
        }
        return skillCapabilityService.resolveUniqueSkillForOutputFormat(targetFormat).orElse(null);
    }

    private boolean shouldUseObjectiveForSkillInference(
            String stepDescription,
            Set<String> requiredTools,
            Map<String, Object> parameters
    ) {
        if (requiredTools != null && (requiredTools.contains("callMcpTool") || requiredTools.contains("createPlan"))) {
            return false;
        }

        String description = stepDescription == null ? "" : stepDescription.toLowerCase();
        if (mentionsAny(description, ".pdf", ".docx", ".pptx", ".md", "pdf", "docx", "pptx", "markdown")) {
            return true;
        }
        if (mentionsAny(description, "export", "render", "convert", "generate file", "write file", "save as")) {
            return true;
        }
        if (mentionsAny(description, "导出", "转换", "生成文件", "保存为", "输出为", "写入文件")) {
            return true;
        }

        Object relativePathValue = parameters == null ? null : parameters.get("relativePath");
        return relativePathValue instanceof String path && inferFormatFromPath(path) != null;
    }

    private boolean mentionsAny(String normalizedText, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldUseObjectiveForDocumentSkillInference(
            String stepDescription,
            String objective,
            Set<String> requiredTools,
            Map<String, Object> parameters
    ) {
        if (requiredTools != null && (requiredTools.contains("callMcpTool") || requiredTools.contains("createPlan"))) {
            return false;
        }

        String description = stepDescription == null ? "" : stepDescription.toLowerCase();
        String objectiveFormat = inferRequestedDeliverableFormat(objective);
        Object relativePathValue = parameters == null ? null : parameters.get("relativePath");
        String pathFormat = relativePathValue instanceof String path && !path.isBlank()
                ? inferFormatFromPath(path)
                : null;

        if (pathFormat != null && ("md".equals(pathFormat) || "txt".equals(pathFormat))) {
            return false;
        }
        if (pathFormat != null && objectiveFormat != null && !objectiveFormat.equals(pathFormat)) {
            return false;
        }

        if (mentionsAny(description, ".pdf", ".docx", ".pptx", "pdf", "docx", "pptx")) {
            return true;
        }
        if (mentionsAny(description, "export", "render", "convert", "output as", "generate pdf", "generate docx", "generate pptx")) {
            return true;
        }

        return pathFormat != null && objectiveFormat != null && objectiveFormat.equals(pathFormat);
    }

    private String normalizeSkillName(String skillName) {
        if (skillName == null) {
            return null;
        }
        String normalized = skillName.trim();
        if (normalized.startsWith("skill:")) {
            normalized = normalized.substring("skill:".length()).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String declaredSkillName(WorkflowStep step) {
        if (step == null || step.parameterContext() == null) {
            return null;
        }
        Object skillNameValue = step.parameterContext().get("skillName");
        if (!(skillNameValue instanceof String skillName) || skillName.isBlank()) {
            return null;
        }
        return resolveActualSkillName(skillName);
    }

    private boolean isExportTransformStep(WorkflowStep step) {
        String skillName = declaredSkillName(step);
        if (skillName == null) {
            return false;
        }

        String targetFormat = inferStepTargetFormat(step);
        if (targetFormat == null || isTextDraftFormat(targetFormat)) {
            return false;
        }

        Set<String> supportedOutputs = supportedOutputFormatsForSkill(skillName);
        return supportedOutputs.isEmpty() || supportedOutputs.contains(targetFormat);
    }

    private boolean isDraftProducerStep(WorkflowStep step) {
        if (step == null) {
            return false;
        }
        if ("project-planning".equals(declaredSkillName(step))) {
            return true;
        }
        List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
        if (requiredTools.contains("createPlan")) {
            return true;
        }

        String targetFormat = inferStepTargetFormat(step);
        if (isTextDraftFormat(targetFormat) && requiredTools.contains("writeWorkspaceFile")) {
            return true;
        }

        String normalized = step.description() == null ? "" : step.description().toLowerCase();
        return !requiredTools.contains("callMcpTool")
                && !isExportTransformStep(step)
                && mentionsAny(
                        normalized,
                        "draft",
                        "compose",
                        "outline",
                        "itinerary",
                        "schedule",
                        "plan",
                        "markdown",
                        "structured content",
                        "草案",
                        "初稿",
                        "整理",
                        "生成结构化内容",
                        "行程",
                        "计划",
                        "安排"
                );
    }

    private boolean isTextDraftFormat(String format) {
        return "md".equals(format) || "txt".equals(format) || "html".equals(format);
    }

    private boolean isTerminalDeliveryStep(WorkflowStep step) {
        if (step == null) {
            return false;
        }
        if (isExportTransformStep(step)) {
            return true;
        }
        return isPureWorkspaceWriteStep(step);
    }

    private boolean isPureWorkspaceWriteStep(WorkflowStep step) {
        if (step == null) {
            return false;
        }
        if ("project-planning".equals(declaredSkillName(step))) {
            return false;
        }
        List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
        return requiredTools.contains("writeWorkspaceFile")
                && !requiredTools.contains("createPlan")
                && !requiredTools.contains("callMcpTool")
                && declaredSkillName(step) == null;
    }

    private void trimUnrequestedTerminalDeliverySteps(String objective, List<WorkflowStep> steps) {
        if (steps == null || steps.isEmpty() || explicitlyRequestsFileDeliverable(objective)) {
            return;
        }
        while (!steps.isEmpty() && isTerminalDeliveryStep(steps.get(steps.size() - 1))) {
            steps.remove(steps.size() - 1);
        }
    }

    private void removeRedundantOfficeDeliveryWrites(List<WorkflowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }

        List<WorkflowStep> filtered = new ArrayList<>();
        LinkedHashSet<String> exportedFormats = new LinkedHashSet<>();
        LinkedHashSet<String> exportedPaths = new LinkedHashSet<>();

        for (WorkflowStep step : steps) {
            String targetFormat = inferStepTargetFormat(step);
            String relativePath = relativePath(step);

            if (isDuplicateOfficeExportStep(step, targetFormat, relativePath, exportedFormats, exportedPaths)) {
                continue;
            }

            if (isExportTransformStep(step)) {
                filtered.add(step);
                if (targetFormat != null && !targetFormat.isBlank()) {
                    exportedFormats.add(targetFormat);
                }
                if (relativePath != null) {
                    exportedPaths.add(normalizeValue(relativePath));
                }
                continue;
            }

            if (isRedundantOfficeWriteStep(step, targetFormat, relativePath, exportedFormats, exportedPaths)) {
                continue;
            }

            filtered.add(step);
        }

        steps.clear();
        steps.addAll(filtered);
    }

    private boolean isDuplicateOfficeExportStep(
            WorkflowStep step,
            String targetFormat,
            String relativePath,
            Set<String> exportedFormats,
            Set<String> exportedPaths
    ) {
        if (step == null || targetFormat == null || isTextDraftFormat(targetFormat)) {
            return false;
        }
        if (!isExportTransformStep(step)) {
            return false;
        }

        if (relativePath != null && exportedPaths.contains(normalizeValue(relativePath))) {
            return true;
        }
        return exportedFormats.contains(targetFormat);
    }

    private boolean isRedundantOfficeWriteStep(
            WorkflowStep step,
            String targetFormat,
            String relativePath,
            Set<String> exportedFormats,
            Set<String> exportedPaths
    ) {
        if (step == null || targetFormat == null || isTextDraftFormat(targetFormat)) {
            return false;
        }
        if (!isWorkspaceWriteStep(step)) {
            return false;
        }

        if (relativePath != null && exportedPaths.contains(normalizeValue(relativePath))) {
            return true;
        }
        return exportedFormats.contains(targetFormat);
    }

    private boolean isWorkspaceWriteStep(WorkflowStep step) {
        if (step == null) {
            return false;
        }
        List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
        return requiredTools.contains("writeWorkspaceFile")
                && !requiredTools.contains("createPlan")
                && !requiredTools.contains("callMcpTool");
    }

    private String relativePath(WorkflowStep step) {
        if (step == null || step.parameterContext() == null) {
            return null;
        }
        Object value = step.parameterContext().get("relativePath");
        if (value instanceof String path && !path.isBlank()) {
            return path.trim();
        }
        return null;
    }

    private WorkflowStep buildDraftPreparationStep(
            String objective,
            WorkflowStep exportStep,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        String agentId = selectDraftAgent(exportStep, agentSnapshots);
        if (agentId == null || agentId.isBlank()) {
            return null;
        }

        String targetFormat = Optional.ofNullable(inferStepTargetFormat(exportStep)).orElse("document");
        String description = ResponseLanguageHelper.choose(
                objective,
                "使用 project-planning 技能整理结构化内容草案，并保存为 Markdown 文件，供后续导出为 ." + targetFormat + " 结果",
                "Use the project-planning skill to compose a structured draft and save it as a Markdown file for the later ."
                        + targetFormat + " export"
        );

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("skillName", "project-planning");
        parameters.put("planId", "draft_for_" + targetFormat + "_deliverable");
        parameters.put("relativePath", defaultDraftPathForExportStep(exportStep, targetFormat));

        return new WorkflowStep(
                agentId,
                description,
                List.of("read_skill", "createPlan", "writeWorkspaceFile"),
                parameters
        );
    }

    private String selectDraftAgent(WorkflowStep exportStep, List<AgentCapabilitySnapshot> agentSnapshots) {
        if (agentSnapshots == null || agentSnapshots.isEmpty()) {
            return exportStep == null ? "manus" : exportStep.agent();
        }
        String exportAgent = exportStep == null ? null : exportStep.agent();
        if (exportAgent != null) {
            Optional<AgentCapabilitySnapshot> matchingExportAgent = agentSnapshots.stream()
                    .filter(snapshot -> exportAgent.equals(snapshot.agentId()))
                    .findFirst();
            if (matchingExportAgent.isPresent() && matchingExportAgent.get().hasSkill("project-planning")) {
                return exportAgent;
            }
        }
        return agentSnapshots.stream()
                .filter(snapshot -> snapshot.hasSkill("project-planning"))
                .map(AgentCapabilitySnapshot::agentId)
                .findFirst()
                .orElse(exportAgent);
    }

    private String defaultDraftPathForExportStep(WorkflowStep exportStep, String targetFormat) {
        if (exportStep != null && exportStep.parameterContext() != null) {
            Object relativePathValue = exportStep.parameterContext().get("relativePath");
            if (relativePathValue instanceof String relativePath && !relativePath.isBlank()) {
                return convertToTextDraftPath(relativePath);
            }
        }
        return "output/" + normalizeValue(targetFormat) + "_draft.md";
    }

    private String inferRequestedDeliverableFormat(String text) {
        String format = inferFormatFromText(text);
        if (format == null || format.isBlank()) {
            return null;
        }
        if (looksLikeRagIngestionRequest(text)) {
            return null;
        }
        if (isTextDraftFormat(format) && !explicitlyRequestsConcreteTextDeliverable(text)) {
            return null;
        }
        return format;
    }

    private void validateObjectiveCapabilitySupportOrThrow(String objective, List<AgentCapabilitySnapshot> agentSnapshots) {
        String requestedDeliverableFormat = inferRequestedDeliverableFormat(objective);
        if (requestedDeliverableFormat == null || requestedDeliverableFormat.isBlank()) {
            return;
        }
        if (environmentSupportsDeliverable(requestedDeliverableFormat, agentSnapshots)) {
            return;
        }
        throw new PlanValidationException(
                buildUnsupportedDeliverableMessage(objective, requestedDeliverableFormat, agentSnapshots),
                List.of()
        );
    }

    private boolean explicitlyRequestsFileDeliverable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        if (inferRequestedDeliverableFormat(normalized) != null) {
            return true;
        }
        return mentionsAny(
                normalized,
                "export",
                "save",
                "write file",
                "workspace",
                "document",
                "file",
                "deliverable",
                "导出",
                "保存",
                "写入文件",
                "文件",
                "文档",
                "工作区"
        );
    }

    private boolean explicitlyRequestsConcreteTextDeliverable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        return mentionsAny(
                normalized,
                "输出为",
                "导出为",
                "保存为",
                "生成",
                "write to",
                "save as",
                "export as",
                "output as",
                "deliverable",
                "最终文件",
                "最终文档",
                "markdown 文档",
                "markdown文件",
                "text file",
                "plain text"
        );
    }

    private boolean looksLikeRagIngestionRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        return mentionsAny(
                normalized,
                "rag_ingest",
                "ingest into",
                "ingest",
                "导入知识库",
                "导入到知识库",
                "写入知识库",
                "知识库"
        );
    }

    private String inferStepTargetFormat(WorkflowStep step) {
        if (step == null) {
            return null;
        }

        Map<String, Object> parameters = step.parameterContext();
        if (parameters != null) {
            Object relativePath = parameters.get("relativePath");
            if (relativePath instanceof String path && !path.isBlank()) {
                String formatFromPath = inferFormatFromPath(path);
                if (formatFromPath != null) {
                    return formatFromPath;
                }
            }
        }

        return inferFormatFromText(step.description());
    }

    private String inferFormatFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.toLowerCase();
        if (mentionsAny(normalized, ".docx", "word", "docx")) {
            return "docx";
        }
        if (mentionsAny(normalized, ".pptx", "pptx", "powerpoint", "slides")) {
            return "pptx";
        }
        if (mentionsAny(normalized, ".pdf", "pdf", "portable document")) {
            return "pdf";
        }
        if (mentionsAny(normalized, ".md", "markdown")) {
            return "md";
        }
        if (mentionsAny(normalized, ".txt", "text file", "plain text")) {
            return "txt";
        }
        if (mentionsAny(normalized, ".html", "html")) {
            return "html";
        }
        return null;
    }

    private String inferFormatFromPath(String path) {
        String normalized = normalizeValue(path);
        if (normalized.endsWith(".docx")) {
            return "docx";
        }
        if (normalized.endsWith(".pptx")) {
            return "pptx";
        }
        if (normalized.endsWith(".pdf")) {
            return "pdf";
        }
        if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) {
            return "md";
        }
        if (normalized.endsWith(".txt")) {
            return "txt";
        }
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) {
            return "html";
        }
        return null;
    }

    private Set<String> supportedOutputFormatsForSkill(String skillName) {
        return skillCapabilityService.getCapability(skillName)
                .map(capability -> Set.copyOf(capability.outputFormats()))
                .orElse(Set.of());
    }

    private boolean environmentSupportsDeliverable(String format, List<AgentCapabilitySnapshot> agentSnapshots) {
        if (format == null || format.isBlank()) {
            return true;
        }
        if (isTextDraftFormat(format)) {
            return agentSnapshots != null && agentSnapshots.stream().anyMatch(snapshot -> snapshot.hasLocalTool("writeWorkspaceFile"));
        }
        // Check local tools for PDF support
        if ("pdf".equalsIgnoreCase(format)) {
            return agentSnapshots != null && agentSnapshots.stream()
                    .anyMatch(snapshot -> snapshot.hasLocalTool("markdownToPdf")
                            || snapshot.hasLocalTool("htmlToPdf")
                            || snapshot.hasLocalTool("markdownFileToPdf"));
        }
        // Check skills for other formats
        return skillCapabilityService.listAvailableCapabilities().stream()
                .anyMatch(capability -> capability.outputFormats().contains(format));
    }

    private boolean stepCanProduceDeliverable(WorkflowStep step, String requestedDeliverableFormat) {
        if (step == null || requestedDeliverableFormat == null || requestedDeliverableFormat.isBlank()) {
            return false;
        }
        if (isTextDraftFormat(requestedDeliverableFormat)) {
            List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
            return requiredTools.contains("writeWorkspaceFile")
                    && requestedDeliverableFormat.equals(inferStepTargetFormat(step));
        }
        // Check for PDF via local tools
        if ("pdf".equalsIgnoreCase(requestedDeliverableFormat)) {
            List<String> requiredTools = step.requiredTools() == null ? List.of() : step.requiredTools();
            return requiredTools.contains("markdownToPdf")
                    || requiredTools.contains("htmlToPdf")
                    || requiredTools.contains("markdownFileToPdf");
        }
        // Check skills for other formats
        String skillName = declaredSkillName(step);
        return skillName != null && supportedOutputFormatsForSkill(skillName).contains(requestedDeliverableFormat);
    }

    private String buildUnsupportedDeliverableMessage(
            String objective,
            String requestedDeliverableFormat,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        String formatLabel = formatDisplayName(requestedDeliverableFormat);
        String fallbackSuggestion = fallbackSuggestion(objective, requestedDeliverableFormat, agentSnapshots);
        return ResponseLanguageHelper.choose(
                objective,
                "当前环境暂不支持直接输出 " + formatLabel + " 文件，因此本次无法继续执行。"
                        + (fallbackSuggestion == null ? "" : "\n\n" + fallbackSuggestion),
                "The current environment does not support direct " + formatLabel + " output, so this request cannot continue as-is."
                        + (fallbackSuggestion == null ? "" : "\n\n" + fallbackSuggestion)
        );
    }

    private String fallbackSuggestion(String objective, String requestedDeliverableFormat, List<AgentCapabilitySnapshot> agentSnapshots) {
        boolean chinese = ResponseLanguageHelper.detect(objective) == ResponseLanguageHelper.Language.ZH_CN;
        if (isTextDraftFormat(requestedDeliverableFormat)) {
            return chinese
                    ? "建议检查当前 agent 是否允许使用 writeWorkspaceFile，或改为直接聊天输出。"
                    : "Consider enabling writeWorkspaceFile for a planning-visible agent, or request a direct chat response instead.";
        }
        boolean canWriteText = environmentSupportsDeliverable("md", agentSnapshots);
        if (canWriteText) {
            return chinese
                    ? "你可以改为输出 Markdown 或纯文本版本；如果需要 Word，请先启用对应的 docx 导出能力后再试。"
                    : "You can request Markdown or plain text output instead. If you need Word output, enable the corresponding docx export capability and try again.";
        }
        return chinese
                ? "如需继续，请先启用对应的导出能力，或改为当前环境支持的输出格式。"
                : "To continue, enable the required export capability first or switch to an output format supported by the current environment.";
    }

    private String formatDisplayName(String format) {
        if (format == null || format.isBlank()) {
            return "requested";
        }
        return switch (format) {
            case "docx" -> "Word (.docx)";
            case "pdf" -> "PDF";
            case "pptx" -> "PowerPoint (.pptx)";
            case "md" -> "Markdown";
            case "txt" -> "plain text";
            case "html" -> "HTML";
            default -> "." + format;
        };
    }

    private List<String> listAvailableSkillNames() {
        if (!skillsService.isEnabled()) {
            return List.of();
        }
        return skillsService.listSkills().stream()
                .map(SkillInfoResponse::name)
                .map(this::normalizeSkillName)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isOfficeDocumentPath(String path) {
        String normalized = normalizeValue(path);
        return normalized.endsWith(".docx")
                || normalized.endsWith(".pptx")
                || normalized.endsWith(".pdf");
    }

    private String convertToTextDraftPath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return path + ".md";
        }
        return path.substring(0, dot) + "_draft.md";
    }

    private boolean mentionsPlanDraft(String stepDescription, String objective) {
        String combined = (stepDescription + "\n" + objective).toLowerCase();
        return combined.contains("plan")
                || combined.contains("schedule")
                || combined.contains("checklist")
                || combined.contains("规划")
                || combined.contains("计划")
                || combined.contains("行程");
    }

    private String buildSkillPlanningGuidance() {
        if (!skillsService.isEnabled()) {
            return "AVAILABLE SKILLS:\n- No skills are enabled.";
        }

        List<SkillInfoResponse> skills = skillsService.listSkills();
        Map<String, SkillCapabilityDescriptor> capabilities = skillCapabilityService.listAvailableCapabilities().stream()
                .collect(Collectors.toMap(
                        capability -> normalizeSkillName(capability.skillName()),
                        capability -> capability,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        StringBuilder builder = new StringBuilder();
        builder.append("AVAILABLE SKILLS AND HOW TO USE THEM:\n\n");
        builder.append("Virtual Tool: read_skill\n");
        builder.append("Description: Read the instructions of an allowed skill before using it.\n\n");
        builder.append("Required Parameters:\n");
        builder.append("  - skillName (string): Exact skill name from the list below.\n\n");
        builder.append("Expected Output Format:\n");
        builder.append("  The skill instructions and workflow guidance.\n\n");
        builder.append("Planning rules for skills:\n");
        builder.append("- If a step needs a skill, include read_skill in requiredTools and set parameters.skillName to the exact skill name.\n");
        builder.append("- A skill is not the same thing as a local tool. Do not refer to a skill name as if it were a callable local tool.\n");
        builder.append("- Do not automatically add local tools just because a skill may internally use them during execution.\n");
        builder.append("- For file exports, prefer skills whose inferred outputFormats match the requested deliverable format.\n");
        builder.append("- Do not assume fixed skill names. Use the actual skill names listed below.\n");
        builder.append("- Skills remain loadable capabilities via read_skill. Local tools should appear in requiredTools only when the step explicitly invokes them.\n\n");
        builder.append("Available skill names:\n");
        for (SkillInfoResponse skill : skills) {
            builder.append("- ")
                    .append(skill.name())
                    .append(": ")
                    .append(skill.description());
            SkillCapabilityDescriptor capability = capabilities.get(normalizeSkillName(skill.name()));
            if (capability != null) {
                builder.append(" [operations=")
                        .append(capability.operations().isEmpty() ? "none" : String.join(", ", capability.operations()))
                        .append(", aliases=")
                        .append(capability.aliases().isEmpty() ? "none" : String.join(", ", capability.aliases()))
                        .append(", inputFormats=")
                        .append(capability.inputFormats().isEmpty() ? "none" : String.join(", ", capability.inputFormats()))
                        .append(", outputFormats=")
                        .append(capability.outputFormats().isEmpty() ? "none" : String.join(", ", capability.outputFormats()))
                        .append(", executionHints=")
                        .append(capability.executionHints().isEmpty() ? "none" : String.join(", ", capability.executionHints()))
                        .append("]");
                if (!capability.planningHint().isBlank()) {
                    builder.append(" Hint: ").append(capability.planningHint());
                }
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    public static class PlanValidationException extends RuntimeException {
        private final List<WorkflowStep> plannedSteps;

        public PlanValidationException(String message, List<WorkflowStep> plannedSteps) {
            super(message);
            this.plannedSteps = plannedSteps == null ? List.of() : List.copyOf(plannedSteps);
        }

        public List<WorkflowStep> getPlannedSteps() {
            return plannedSteps;
        }
    }

    private record DraftPlan(
            List<WorkflowStep> steps,
            List<String> lintWarnings
    ) {
        private DraftPlan {
            steps = steps == null ? List.of() : List.copyOf(steps);
            lintWarnings = lintWarnings == null ? List.of() : List.copyOf(lintWarnings);
        }
    }
}
