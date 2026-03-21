package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
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
    private final ToolRegistryService toolRegistryService;
    private final ObjectMapper objectMapper;

    public PlanningService(ChatClient chatClient, ToolRegistryService toolRegistryService) {
        this.chatClient = chatClient;
        this.toolRegistryService = toolRegistryService;
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

    public List<WorkflowStep> createWorkflowPlan(String objective, String availableAgents) {
        String toolsSchema = toolRegistryService.generateToolsPromptGuidance();
        String languageDirective = ResponseLanguageHelper.responseDirective(objective);
        Set<String> allowedAgents = parseAllowedAgents(availableAgents);

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
        List<WorkflowStep> enrichedSteps = enrichStepsWithToolMetadata(sanitizedSteps, objective);
        return removeDuplicateWorkflowSteps(enrichedSteps);
    }

    private List<WorkflowStep> enrichStepsWithToolMetadata(List<WorkflowStep> steps, String objective) {
        return steps.stream().map(step -> {
            try {
                String extractionPrompt = """
                        Analyze this workflow step and extract the required tools and parameters.

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
                        """.formatted(step.description(), objective);

                String jsonResponse = chatClient.prompt()
                        .user(extractionPrompt)
                        .call()
                        .content();

                JsonNode jsonNode = objectMapper.readTree(stripMarkdownCodeFence(jsonResponse));

                List<String> requiredTools = new ArrayList<>();
                if (jsonNode.has("requiredTools") && jsonNode.get("requiredTools").isArray()) {
                    for (JsonNode tool : jsonNode.get("requiredTools")) {
                        String toolName = tool.asText();
                        if (toolRegistryService.getTool(toolName).isPresent()) {
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

                log.info("Enriched step: {} -> tools={}, params={}", step.description(), requiredTools, parameters);
                return new WorkflowStep(step.agent(), step.description(), requiredTools, parameters);
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

    private Set<String> resolveAllowedParameterNames(List<String> requiredTools) {
        return requiredTools.stream()
                .map(toolRegistryService::getTool)
                .flatMap(Optional::stream)
                .flatMap(tool -> tool.getParameters().stream())
                .map(param -> param.getName())
                .collect(Collectors.toSet());
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
}
