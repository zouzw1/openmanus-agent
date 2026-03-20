package com.openmanus.saa.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.service.ToolRegistryService;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.tool.McpToolBridge;
import com.openmanus.saa.tool.PlanningTools;
import com.openmanus.saa.tool.WorkspaceTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class DataAnalysisAgentExecutor implements SpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(DataAnalysisAgentExecutor.class);

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final WorkspaceTools workspaceTools;
    private final PlanningTools planningTools;
    private final McpToolBridge mcpToolBridge;
    private final McpPromptContextService mcpPromptContextService;
    private final ToolRegistryService toolRegistryService;
    private final ObjectMapper objectMapper;

    public DataAnalysisAgentExecutor(
            ChatClient chatClient,
            OpenManusProperties properties,
            WorkspaceTools workspaceTools,
            PlanningTools planningTools,
            McpToolBridge mcpToolBridge,
            McpPromptContextService mcpPromptContextService,
            ToolRegistryService toolRegistryService
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.workspaceTools = workspaceTools;
        this.planningTools = planningTools;
        this.mcpToolBridge = mcpToolBridge;
        this.mcpPromptContextService = mcpPromptContextService;
        this.toolRegistryService = toolRegistryService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "data_analysis";
    }

    @Override
    public String description() {
        return "Specialist agent for metrics interpretation, tabular analysis, and report drafting.";
    }

    @Override
    public String execute(String objective, String currentPlan, String step) {
        log.info("=== DataAnalysisAgent Execution ===");
        log.info("Objective: {}", objective);
        log.info("Current Plan: {}", currentPlan);
        log.info("Step: {}", step);
        
        // 获取所有工具的 Schema，用于结构化参数抽取
        String toolsSchema = toolRegistryService.generateToolsPromptGuidance();
        
        // 添加上下文提示，告诉 LLM 如何处理工具调用失败
        String contextHint = """
                
                STRUCTURED PARAMETER EXTRACTION WORKFLOW (WITHIN CURRENT STEP):
                
                PHASE 1 - TOOL IDENTIFICATION:
                Based on the step description, identify which tool(s) from the schema above are needed.
                
                PHASE 2 - PARAMETER EXTRACTION:
                For each required tool, extract parameters using this process:
                1. Check the tool's parameter schema - note ALL required parameters
                2. Search conversation history and context for each required parameter
                3. If found: Use that value
                4. If NOT found: Mark as missing - do NOT assume defaults
                
                PHASE 3 - VALIDATION:
                Before calling the tool, verify:
                ✓ All required parameters are present
                ✓ Parameter types match the schema (string, number, boolean, etc.)
                ✓ Enum values are valid (if applicable)
                
                PHASE 4 - EXECUTION WITH RETRY:
                - Call the tool with extracted parameters
                - If fails due to missing/invalid params: RETRY IMMEDIATELY in this step
                - Check history again for the missing value
                - If still not found: Ask user explicitly and END THIS STEP
                
                OUTPUT FORMAT:
                Always structure tool calls as JSON matching the schema:
                ```json
                {
                  "tool": "tool_name",
                  "parameters": {
                    "param1": "value1",
                    "param2": "value2"
                  }
                }
                ```
                
                CRITICAL RULES:
                - NEVER fabricate data or assume default values
                - ALWAYS validate against the tool schema before calling
                - All retries must happen WITHIN THE CURRENT STEP
                - If a required parameter is missing and not in history, ask the user
                """;
        
        String result = chatClient.prompt()
                .system("""
                        %s

                        %s

                        You are the DATA_ANALYSIS executor.
                        Prefer structured outputs, observations, assumptions, and concise conclusions.
                        If raw data is unavailable, explicitly state the gap instead of inventing numbers.
                        
                        %s
                        
                        CRITICAL: 
                        - NEVER fabricate data, metrics, or analysis results.
                        - ALWAYS use available tools to fetch real data when possible.
                        - If data cannot be obtained, clearly state "Data unavailable" rather than making assumptions.
                        - For location-specific queries (e.g., weather), if location is not specified, ASK the user to provide it.
                        - Do NOT assume default values for cities, locations, or other context-dependent parameters.
                        """.formatted(properties.getSystemPrompt(), toolsSchema, contextHint))
                .user("""
                        Objective: %s

                        Current plan:
                        %s

                        Execute only this step and summarize the result:
                        %s
                        """.formatted(objective, currentPlan, step))
                .tools(workspaceTools, planningTools, mcpToolBridge)
                .call()
                .content();
        
        log.info("Result: {}", result);
        log.info("====================================");
        
        return result;
    }
}
