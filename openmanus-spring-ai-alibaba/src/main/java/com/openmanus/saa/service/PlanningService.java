package com.openmanus.saa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.WorkflowStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        String content = chatClient.prompt()
                .system("""
                        You are a planning assistant.
                        Break the user's objective into 3 to 6 concise actionable steps.
                        Return plain text only, one step per line, without numbering prefixes beyond the line content itself.
                        """)
                .user(objective)
                .call()
                .content();

        List<String> steps = Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripPrefix)
                .collect(Collectors.toList());
        return new PlanResponse(objective, steps);
    }

    public List<WorkflowStep> createWorkflowPlan(String objective, String availableAgents) {
        // 获取所有可用工具的 Schema
        String toolsSchema = toolRegistryService.generateToolsPromptGuidance();
        
        // 第一步：让 LLM 制定计划并识别所需工具和参数
        String content = chatClient.prompt()
                .system("""
                        You are a workflow planning assistant.
                        Break the objective into 3 to 6 concise actionable steps.
                        Assign each step to exactly one available agent.
                        Return plain text only.
                        Each line must follow this format:
                        [agent_name] step description

                        Available agents:
                        %s
                        
                        AVAILABLE TOOLS AND THEIR SCHEMAS:
                        %s
                        
                        IMPORTANT PLANNING RULES:
                        1. For each step, identify which tool(s) will be needed based on the tool schemas
                        2. Ensure ALL required parameters for each tool can be obtained from:
                           - User's original request
                           - Previous step outputs
                           - Context/history
                        3. If a required parameter cannot be obtained, mark that step as needing user clarification
                        4. Structure your step descriptions to include the expected tool usage
                        """.formatted(availableAgents, toolsSchema))
                .user(objective)
                .call()
                .content();

        // 解析步骤（包含工具和参数信息）
        List<WorkflowStep> steps = Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripPrefix)
                .map(this::toWorkflowStep)
                .collect(Collectors.toList());
        
        // 第二步：对每个步骤，让 LLM 提取所需的工具和参数
        return enrichStepsWithToolMetadata(steps, objective);
    }
    
    /**
     * 为每个步骤补充工具元数据（requiredTools 和 parameterContext）
     */
    private List<WorkflowStep> enrichStepsWithToolMetadata(List<WorkflowStep> steps, String objective) {
        return steps.stream().map(step -> {
            try {
                // 使用 LLM 从步骤描述中提取工具名和参数
                String extractionPrompt = """
                        Analyze this workflow step and extract the required tools and parameters.
                        
                        Step description: %s
                        Original objective: %s
                        
                        Based on the available tools schema, identify:
                        1. Which tool(s) are needed for this step?
                        2. What parameters can be extracted from the context?
                        
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
                
                // 清理响应（移除可能的 markdown 标记）
                jsonResponse = jsonResponse.replaceAll("```json", "")
                                          .replaceAll("```", "")
                                          .trim();
                
                // 解析 JSON
                var jsonNode = objectMapper.readTree(jsonResponse);
                
                // 提取 requiredTools
                List<String> requiredTools = new ArrayList<>();
                if (jsonNode.has("requiredTools") && jsonNode.get("requiredTools").isArray()) {
                    for (var tool : jsonNode.get("requiredTools")) {
                        requiredTools.add(tool.asText());
                    }
                }
                
                // 提取 parameters
                Map<String, Object> parameters = new HashMap<>();
                if (jsonNode.has("parameters") && jsonNode.get("parameters").isObject()) {
                    var paramsNode = jsonNode.get("parameters");
                    Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        parameters.put(entry.getKey(), entry.getValue().asText());
                    }
                }
                
                log.info("Enriched step: {} -> tools={}, params={}", 
                        step.description(), requiredTools, parameters);
                
                // 创建增强的 WorkflowStep
                return new WorkflowStep(step.agent(), step.description(), requiredTools, parameters);
                
            } catch (Exception e) {
                log.warn("Failed to extract tool metadata for step: {}, using defaults", 
                        step.description(), e);
                // 如果提取失败，返回原始步骤（使用兼容构造函数）
                return new WorkflowStep(step.agent(), step.description());
            }
        }).collect(Collectors.toList());
    }

    private String stripPrefix(String line) {
        return line.replaceFirst("^[-*\\d.\\s]+", "").trim();
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
}
