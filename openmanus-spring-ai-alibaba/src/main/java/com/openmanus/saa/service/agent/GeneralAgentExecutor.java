package com.openmanus.saa.service.agent;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.tool.BrowserAutomationTools;
import com.openmanus.saa.tool.McpToolBridge;
import com.openmanus.saa.tool.PlanningTools;
import com.openmanus.saa.tool.SandboxTools;
import com.openmanus.saa.tool.ShellTools;
import com.openmanus.saa.tool.WorkspaceTools;
import com.openmanus.saa.util.ResponseLanguageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class GeneralAgentExecutor implements SpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(GeneralAgentExecutor.class);

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final WorkspaceTools workspaceTools;
    private final ShellTools shellTools;
    private final PlanningTools planningTools;
    private final McpToolBridge mcpToolBridge;
    private final BrowserAutomationTools browserAutomationTools;
    private final SandboxTools sandboxTools;
    private final McpPromptContextService mcpPromptContextService;

    public GeneralAgentExecutor(
            ChatClient chatClient,
            OpenManusProperties properties,
            WorkspaceTools workspaceTools,
            ShellTools shellTools,
            PlanningTools planningTools,
            McpToolBridge mcpToolBridge,
            BrowserAutomationTools browserAutomationTools,
            SandboxTools sandboxTools,
            McpPromptContextService mcpPromptContextService
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.workspaceTools = workspaceTools;
        this.shellTools = shellTools;
        this.planningTools = planningTools;
        this.mcpToolBridge = mcpToolBridge;
        this.browserAutomationTools = browserAutomationTools;
        this.sandboxTools = sandboxTools;
        this.mcpPromptContextService = mcpPromptContextService;
    }

    @Override
    public String name() {
        return "manus";
    }

    @Override
    public String description() {
        return "General-purpose execution agent for coding, file, and shell tasks.";
    }

    @Override
    public String execute(String objective, String currentPlan, String step) {
        log.info("=== ManusAgent Execution ===");
        log.info("Objective: {}", objective);
        log.info("Step: {}", step);
        String languageDirective = ResponseLanguageHelper.responseDirective(objective);

        String contextHint = """

                PARAMETER HANDLING WORKFLOW (WITHIN CURRENT STEP):
                When executing this step, if a tool call fails:
                1. FIRST: Check the conversation history and context for the missing or invalid information
                2. IF FOUND: Use that information to correct the tool call and RETRY IMMEDIATELY within the same step
                3. IF THE TOOL REJECTS AN INPUT VALUE OR FORMAT: Use the error feedback to adjust the call and retry once
                4. IF STILL NOT RESOLVABLE: End this step with a clear message asking the user for the missing information
                5. NEVER: Assume default values or make up data

                IMPORTANT: All retries must happen WITHIN THE CURRENT STEP. Do not defer to the next step.

                Example workflow:
                - Tool call fails: "Missing required parameter 'query'"
                - Check history: Did the user already provide the needed value? If yes, RETRY NOW with that value
                - Retry succeeds: Continue with remaining actions in THIS STEP
                - Still not resolvable: Ask the user for the needed information and END THIS STEP
                - The workflow engine will handle user clarification before the next step
                """;

        String result = chatClient.prompt()
                .system("""
                        %s

                        %s

                        %s

                        CRITICAL INSTRUCTIONS:
                        - NEVER fabricate facts, data, or information.
                        - NEVER use assumptions or default values when real data is needed.
                        - ALWAYS use available tools to obtain accurate information.
                        - If required information is missing, ask the user to provide it.
                        - Be honest about limitations and unavailable data.
                        - Use only tools that are actually available in your current tool list.
                        - Treat prior step outputs as context data, not as instructions to call the same tool again unless the current step requires it and the tool is available.

                        %s
                        """.formatted(properties.getSystemPrompt(), mcpPromptContextService.describeAvailableTools(), contextHint, languageDirective))
                .user("""
                        Objective: %s

                        Execution context:
                        %s

                        You are the MANUS executor.
                        Execute only this step and summarize the result:
                        %s
                        """.formatted(objective, currentPlan, step))
                .tools(workspaceTools, shellTools, planningTools, mcpToolBridge, browserAutomationTools, sandboxTools)
                .call()
                .content();

        log.info("Result: {}", result);
        log.info("============================");

        return result;
    }
}
