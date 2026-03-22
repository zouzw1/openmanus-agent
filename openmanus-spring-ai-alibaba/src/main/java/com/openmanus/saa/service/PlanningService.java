package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentCapabilitySnapshot;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.SkillCapabilityDescriptor;
import com.openmanus.saa.model.SkillInfoResponse;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.mcp.McpService;
import com.openmanus.saa.service.mcp.McpPromptContextService;
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
        DraftPlan draftPlan = createWorkflowDraft(objective, agentSnapshots);
        draftPlan.lintWarnings().forEach(warning -> log.warn("Workflow draft lint: {}", warning));
        return compileWorkflowPlan(objective, draftPlan.steps(), agentSnapshots);
    }

    private DraftPlan createWorkflowDraft(String objective, List<AgentCapabilitySnapshot> agentSnapshots) {
        String toolsSchema = buildPlanningToolGuidance();
        String languageDirective = ResponseLanguageHelper.responseDirective(objective);
        String availableAgents = buildAgentCapabilityPrompt(agentSnapshots);
        Set<String> allowedAgents = agentSnapshots.stream()
                .map(AgentCapabilitySnapshot::agentId)
                .filter(agentId -> agentId != null && !agentId.isBlank())
                .collect(Collectors.toSet());

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

                        AVAILABLE TOOLS AND THEIR SCHEMAS:
                        %s

                        IMPORTANT PLANNING RULES:
                        1. Each step must be one executable action.
                        2. The "agent" field MUST be one of the available agents, never a tool name.
                        3. Never output meta steps such as "we need to..." or "the first step is...".
                        4. Never restate the task as a step.
                        5. If a required parameter cannot be obtained, create at most one explicit clarification step.
                        6. Do not repeat the same tool call for the same target unless the task explicitly requires it.
                        7. Do not invent pseudo-parameter names, slot names, aliases, or fake schema fields inside step descriptions.
                        8. If information is missing, describe the missing information in plain language.
                        9. When the task explicitly asks for a formatted Word, PDF, PPTX, or similar deliverable and an appropriate skill is available, prefer a step that uses that skill instead of a plain text file write.
                        10. Separate collection, composition, and export work into different steps when the final deliverable requires transformation.
                        11. Before any export/render/convert step for a non-text deliverable, include an earlier step that composes the source content or writes a draft artifact.
                        12. Do not use export/render skills such as docx/pdf/pptx to invent the underlying content plan; use a drafting/composition step first.

                        Return JSON only as an array.
                        Each item must follow this schema:
                        {
                          "agent": "manus",
                          "description": "use the appropriate tool to retrieve the required data"
                        }

                        %s
                        """.formatted(availableAgents, toolsSchema, languageDirective))
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

    private List<WorkflowStep> compileWorkflowPlan(
            String objective,
            List<WorkflowStep> draftSteps,
            List<AgentCapabilitySnapshot> agentSnapshots
    ) {
        List<WorkflowStep> draftSafeSteps = draftSteps == null ? List.of() : draftSteps;
        List<WorkflowStep> enrichedSteps = enrichStepsWithToolMetadata(draftSafeSteps, objective);
        List<WorkflowStep> deduplicatedSteps = removeDuplicateWorkflowSteps(enrichedSteps);
        List<WorkflowStep> normalizedSteps = enforceWorkflowStructure(objective, deduplicatedSteps, agentSnapshots);
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
            boolean environmentSupportsDeliverable = skillCapabilityService.listAvailableCapabilities().stream()
                    .anyMatch(capability -> capability.outputFormats().contains(requestedDeliverableFormat));
            boolean planContainsDeliverableProducer = workflowSteps.stream()
                    .map(this::declaredSkillName)
                    .filter(Objects::nonNull)
                    .anyMatch(skillName -> supportedOutputFormatsForSkill(skillName).contains(requestedDeliverableFormat));

            if (!environmentSupportsDeliverable) {
                errors.add("The objective requests a ." + requestedDeliverableFormat
                        + " deliverable, but the current environment has no skill that can produce this format.");
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

                        Based on the available tools schema, identify:
                        1. Which tool(s) are needed for this step?
                        2. What parameters can be extracted from the context?
                        3. Use ONLY exact tool names and exact parameter names from the provided schemas.
                        4. If a parameter name is not explicitly defined in the tool schema, do not invent an alias.

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
                + "\n\n"
                + buildSkillPlanningGuidance()
                + "\n\n"
                + mcpPromptContextService.describeAvailableTools();
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
        String inferredSkillName = existingSkillName.isBlank()
                ? resolveSkillNameForStep(stepDescription, parameters)
                : normalizeSkillName(existingSkillName);
        if (inferredSkillName == null) {
            return;
        }

        parameters.put("skillName", inferredSkillName);
        requiredTools.add("read_skill");
    }

    private String resolveSkillNameForStep(String stepDescription, Map<String, Object> parameters) {
        String explicitSkill = skillCapabilityService.findMentionedSkillName(stepDescription)
                .map(this::normalizeSkillName)
                .orElse(null);
        if (explicitSkill != null) {
            return explicitSkill;
        }

        String targetFormat = inferTargetFormatForSkillResolution(stepDescription, parameters);
        if (targetFormat == null || !looksLikeSkillManagedOutputStep(stepDescription, parameters, targetFormat)) {
            return null;
        }

        return skillCapabilityService.resolveUniqueSkillForOutputFormat(targetFormat)
                .map(this::normalizeSkillName)
                .orElse(null);
    }

    private String inferTargetFormatForSkillResolution(String stepDescription, Map<String, Object> parameters) {
        if (parameters != null) {
            Object relativePath = parameters.get("relativePath");
            if (relativePath instanceof String path && !path.isBlank()) {
                String formatFromPath = inferFormatFromPath(path);
                if (formatFromPath != null) {
                    return formatFromPath;
                }
            }
        }
        return inferFormatFromText(stepDescription);
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
        String normalized = text.toLowerCase();
        List<String> availableSkillNames = skillsService.listSkills().stream()
                .map(SkillInfoResponse::name)
                .toList();
        if (mentionsAny(normalized, "word", "docx", ".docx")) {
            return pickAvailableSkill(availableSkillNames, "docx");
        }
        if (mentionsAny(normalized, "ppt", "pptx", "powerpoint", "slides")) {
            return pickAvailableSkill(availableSkillNames, "pptx");
        }
        if (mentionsAny(normalized, "pdf", "portable document")) {
            return pickAvailableSkill(availableSkillNames, "pdf");
        }
        if (mentionsAny(normalized, "markdown-converter", "convert to markdown", "export markdown", "markdown conversion")) {
            return pickAvailableSkill(availableSkillNames, "markdown-converter");
        }
        return availableSkillNames.stream()
                .filter(skillName -> normalized.contains(skillName.toLowerCase()))
                .findFirst()
                .map(this::normalizeSkillName)
                .orElse(null);
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

    private String pickAvailableSkill(List<String> availableSkillNames, String preferredSkillName) {
        return availableSkillNames.stream()
                .filter(skillName -> preferredSkillName.equalsIgnoreCase(skillName))
                .findFirst()
                .map(this::normalizeSkillName)
                .orElse(null);
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
        return normalizeSkillName(skillName);
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
        return inferFormatFromText(text);
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
        builder.append("- For Word/.docx deliverables prefer skillName=docx.\n");
        builder.append("- For PDF deliverables prefer skillName=pdf.\n");
        builder.append("- For PowerPoint/PPTX deliverables prefer skillName=pptx.\n");
        builder.append("- For Markdown conversion prefer skillName=markdown-converter.\n");
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
