package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.agent.AgentRuntimeFactory;
import com.openmanus.saa.agent.ResolvedAgentRuntime;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.service.intent.IntentResolutionService;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ManusAgentService {

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final PlanningService planningService;
    private final RequestRoutingService requestRoutingService;
    private final SessionMemoryService sessionMemoryService;
    private final WorkflowService workflowService;
    private final AgentRegistryService agentRegistryService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final IntentResolutionService intentResolutionService;

    public ManusAgentService(
            ChatClient chatClient,
            OpenManusProperties properties,
            PlanningService planningService,
            RequestRoutingService requestRoutingService,
            SessionMemoryService sessionMemoryService,
            WorkflowService workflowService,
            AgentRegistryService agentRegistryService,
            AgentRuntimeFactory agentRuntimeFactory,
            IntentResolutionService intentResolutionService
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.planningService = planningService;
        this.requestRoutingService = requestRoutingService;
        this.sessionMemoryService = sessionMemoryService;
        this.workflowService = workflowService;
        this.agentRegistryService = agentRegistryService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.intentResolutionService = intentResolutionService;
    }

    public AgentResponse routeChat(String sessionId, String prompt, String agentId) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        IntentResolution intentResolution = intentResolutionService.resolve(prompt, session);
        return switch (intentResolution.routeMode()) {
            case DIRECT_CHAT -> chat(sessionId, prompt, firstNonBlank(agentId, intentResolution.preferredAgentId()));
            case PLAN_EXECUTE -> executeWithPlan(sessionId, prompt, intentResolution);
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
                plan.steps(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );
    }

    public AgentResponse chat(String sessionId, String prompt, String requestedAgentId) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", prompt);

        AgentDefinition agentDefinition = requestedAgentId == null || requestedAgentId.isBlank()
                ? agentRegistryService.getDefaultChatAgent()
                : agentRegistryService.getEnabled(requestedAgentId).orElseGet(agentRegistryService::getDefaultChatAgent);
        ResolvedAgentRuntime runtime = agentRuntimeFactory.resolve(agentDefinition);
        String history = sessionMemoryService.summarizeHistory(session, 12);
        String languageDirective = ResponseLanguageHelper.responseDirective(prompt);
        String reply = chatClient.prompt()
                .system("""
                        %s
                        
                        %s
                        
                        %s
                        """.formatted(properties.getSystemPrompt(), runtime.systemPrompt(), languageDirective))
                .user("""
                        Conversation history:
                        %s
                        
                        Current user request:
                        %s
                        """.formatted(history, prompt))
                .advisors(runtime.advisors())
                .toolCallbacks(runtime.toolCallbacks())
                .toolContext(runtime.toolContext())
                .call()
                .content();

        session.addMessage("assistant", reply);
        return new AgentResponse(
                "chat",
                prompt,
                reply,
                formatChatMarkdown(prompt, reply),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );
    }

    public AgentResponse executeWithPlan(String sessionId, String objective) {
        return workflowService.executeAsAgentResponse(sessionId, objective);
    }

    public AgentResponse executeWithPlan(String sessionId, String objective, IntentResolution intentResolution) {
        return workflowService.executeAsAgentResponse(sessionId, objective, intentResolution);
    }

    private String firstNonBlank(String requestedAgentId, String preferredAgentId) {
        if (requestedAgentId != null && !requestedAgentId.isBlank()) {
            return requestedAgentId;
        }
        return preferredAgentId;
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
