package com.openmanus.saa.service;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.tool.BrowserAutomationTools;
import com.openmanus.saa.tool.McpToolBridge;
import com.openmanus.saa.tool.PlanningTools;
import com.openmanus.saa.tool.SandboxTools;
import com.openmanus.saa.tool.ShellTools;
import com.openmanus.saa.tool.WorkspaceTools;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ManusAgentService {

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final WorkspaceTools workspaceTools;
    private final ShellTools shellTools;
    private final PlanningTools planningTools;
    private final McpToolBridge mcpToolBridge;
    private final BrowserAutomationTools browserAutomationTools;
    private final SandboxTools sandboxTools;
    private final PlanningService planningService;
    private final RequestRoutingService requestRoutingService;
    private final McpPromptContextService mcpPromptContextService;
    private final SessionMemoryService sessionMemoryService;
    private final WorkflowService workflowService;

    public ManusAgentService(
            ChatClient chatClient,
            OpenManusProperties properties,
            WorkspaceTools workspaceTools,
            ShellTools shellTools,
            PlanningTools planningTools,
            McpToolBridge mcpToolBridge,
            BrowserAutomationTools browserAutomationTools,
            SandboxTools sandboxTools,
            PlanningService planningService,
            RequestRoutingService requestRoutingService,
            McpPromptContextService mcpPromptContextService,
            SessionMemoryService sessionMemoryService,
            WorkflowService workflowService
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.workspaceTools = workspaceTools;
        this.shellTools = shellTools;
        this.planningTools = planningTools;
        this.mcpToolBridge = mcpToolBridge;
        this.browserAutomationTools = browserAutomationTools;
        this.sandboxTools = sandboxTools;
        this.planningService = planningService;
        this.requestRoutingService = requestRoutingService;
        this.mcpPromptContextService = mcpPromptContextService;
        this.sessionMemoryService = sessionMemoryService;
        this.workflowService = workflowService;
    }

    public AgentResponse routeChat(String sessionId, String prompt) {
        RequestRoutingService.RouteMode routeMode = requestRoutingService.decideChatOrPlan(prompt);
        return switch (routeMode) {
            case DIRECT_CHAT -> chat(sessionId, prompt);
            case PLAN_ONLY -> planOnly(sessionId, prompt);
            case PLAN_EXECUTE -> executeWithPlan(sessionId, prompt);
        };
    }

    public AgentResponse planOnly(String sessionId, String objective) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", objective);

        PlanResponse plan = planningService.createPlan(objective);
        String planOutput = plan.summary() != null && !plan.summary().isBlank()
                ? plan.summary()
                : String.join("\n", plan.steps());

        session.addMessage("assistant", planOutput);
        return new AgentResponse(
                "plan",
                objective,
                planOutput,
                planOutput,
                plan.steps()
        );
    }

    public AgentResponse chat(String sessionId, String prompt) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", prompt);

        String history = sessionMemoryService.summarizeHistory(session, 12);
        String languageDirective = ResponseLanguageHelper.responseDirective(prompt);
        String reply = chatClient.prompt()
                .system("""
                        %s

                        %s

                        %s
                        """.formatted(properties.getSystemPrompt(), mcpPromptContextService.describeAvailableTools(), languageDirective))
                .user("""
                        Conversation history:
                        %s

                        Current user request:
                        %s
                        """.formatted(history, prompt))
                .tools(workspaceTools, shellTools, planningTools, mcpToolBridge, browserAutomationTools, sandboxTools)
                .call()
                .content();

        session.addMessage("assistant", reply);
        return new AgentResponse(
                "chat",
                prompt,
                reply,
                formatChatMarkdown(prompt, reply),
                List.of()
        );
    }

    public AgentResponse executeWithPlan(String sessionId, String objective) {
        return workflowService.executeAsAgentResponse(sessionId, objective);
    }

    private String formatChatMarkdown(String prompt, String content) {
        boolean chinese = ResponseLanguageHelper.detect(prompt) == ResponseLanguageHelper.Language.ZH_CN;
        if (chinese) {
            return """
                    ## 回复

                    %s
                    """.formatted(content).trim();
        }
        return """
                ## Reply

                %s
                """.formatted(content).trim();
    }
}
