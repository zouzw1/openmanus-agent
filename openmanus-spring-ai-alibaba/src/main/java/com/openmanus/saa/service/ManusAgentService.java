package com.openmanus.saa.service;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.tool.PlanningTools;
import com.openmanus.saa.tool.ShellTools;
import com.openmanus.saa.tool.McpToolBridge;
import com.openmanus.saa.tool.BrowserAutomationTools;
import com.openmanus.saa.tool.SandboxTools;
import com.openmanus.saa.tool.WorkspaceTools;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final McpPromptContextService mcpPromptContextService;
    private final SessionMemoryService sessionMemoryService;

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
            McpPromptContextService mcpPromptContextService,
            SessionMemoryService sessionMemoryService
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
        this.mcpPromptContextService = mcpPromptContextService;
        this.sessionMemoryService = sessionMemoryService;
    }

    public AgentResponse chat(String sessionId, String prompt) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", prompt);
        String history = sessionMemoryService.summarizeHistory(session, 12);
        String content = chatClient.prompt()
                .system("""
                        %s

                        %s
                        """.formatted(properties.getSystemPrompt(), mcpPromptContextService.describeAvailableTools()))
                .user("""
                        Conversation history:
                        %s

                        Current user request:
                        %s
                        """.formatted(history, prompt))
                .tools(workspaceTools, shellTools, planningTools, mcpToolBridge, browserAutomationTools, sandboxTools)
                .call()
                .content();
        session.addMessage("assistant", content);
        return new AgentResponse("chat", content, List.of());
    }

    public AgentResponse executeWithPlan(String sessionId, String objective) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", objective);
        PlanResponse plan = planningService.createPlan(objective);
        String planId = "plan-" + UUID.randomUUID();
        planningTools.createPlan(planId, plan.steps());

        List<String> results = new ArrayList<>();
        int stepsToRun = Math.min(plan.steps().size(), properties.getMaxSteps());
        for (int i = 0; i < stepsToRun; i++) {
            String step = plan.steps().get(i);
            String stepResult = chatClient.prompt()
                    .system("""
                            %s

                            %s
                            """.formatted(properties.getSystemPrompt(), mcpPromptContextService.describeAvailableTools()))
                    .user("""
                            Objective: %s

                            Current plan:
                            %s

                            Execute only this step and summarize the result:
                            %s
                            """.formatted(objective, planningTools.getPlan(planId), step))
                    .tools(workspaceTools, shellTools, planningTools, mcpToolBridge, browserAutomationTools, sandboxTools)
                    .call()
                    .content();
            results.add("Step " + (i + 1) + ": " + stepResult);
            session.addExecutionLog("Step " + (i + 1) + ": " + step + " => " + stepResult);
        }

        String summary = String.join("\n\n", results);
        session.addMessage("assistant", summary);
        return new AgentResponse("plan-execute", summary, plan.steps());
    }
}
