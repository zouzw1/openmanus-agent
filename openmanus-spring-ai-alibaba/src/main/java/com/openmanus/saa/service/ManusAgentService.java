package com.openmanus.saa.service;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.agent.AgentRuntimeFactory;
import com.openmanus.saa.agent.ResolvedAgentRuntime;
import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.model.AgentTaskStatus;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.SseEvent;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.agent.ReactAgentExecutionSupport;
import com.openmanus.saa.service.intent.IntentResolutionService;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.service.sse.SseEventPublisher;
import com.openmanus.saa.service.supervisor.MultiAgentExecutionResponse;
import com.openmanus.saa.service.supervisor.SupervisorAgentService;
import com.openmanus.saa.util.ResponseLanguageHelper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Service
public class ManusAgentService {

    private static final Logger log = LoggerFactory.getLogger(ManusAgentService.class);

    private final ChatClient chatClient;
    private final OpenManusProperties properties;
    private final PlanningService planningService;
    private final RequestRoutingService requestRoutingService;
    private final SessionMemoryService sessionMemoryService;
    private final WorkflowService workflowService;
    private final AgentRegistryService agentRegistryService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final IntentResolutionService intentResolutionService;
    private final ReactAgentExecutionSupport reactAgentExecutionSupport;
    private final SupervisorAgentService supervisorAgentService;
    private final SseEventPublisher sseEventPublisher;

    public ManusAgentService(
            ChatClient chatClient,
            OpenManusProperties properties,
            PlanningService planningService,
            RequestRoutingService requestRoutingService,
            SessionMemoryService sessionMemoryService,
            WorkflowService workflowService,
            AgentRegistryService agentRegistryService,
            AgentRuntimeFactory agentRuntimeFactory,
            IntentResolutionService intentResolutionService,
            ReactAgentExecutionSupport reactAgentExecutionSupport,
            SupervisorAgentService supervisorAgentService,
            SseEventPublisher sseEventPublisher
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
        this.reactAgentExecutionSupport = reactAgentExecutionSupport;
        this.supervisorAgentService = supervisorAgentService;
        this.sseEventPublisher = sseEventPublisher;
    }

    public AgentResponse routeChat(String sessionId, String prompt, String agentId) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        IntentResolution intentResolution = intentResolutionService.resolve(prompt, session);
        return switch (intentResolution.routeMode()) {
            case DIRECT_CHAT -> chat(sessionId, prompt, firstNonBlank(agentId, intentResolution.preferredAgentId()));
            case PLAN_EXECUTE -> executeWithPlan(sessionId, prompt, intentResolution);
            case MULTI_AGENT -> executeMultiAgent(sessionId, prompt);
        };
    }

    /**
     * Route chat request with full request context including mode override.
     */
    public AgentResponse routeChat(AgentRequest request) {
        Session session = sessionMemoryService.getOrCreate(request.sessionId());
        // IntentResolutionService.resolve() already handles mode override
        IntentResolution resolution = intentResolutionService.resolve(
                request.prompt(), session, request
        );

        // Route based on resolution
        return switch (resolution.routeMode()) {
            case DIRECT_CHAT -> chat(request.sessionId(), request.prompt(), firstNonBlank(request.agentId(), resolution.preferredAgentId()));
            case PLAN_EXECUTE -> executeWithPlan(request.sessionId(), request.prompt(), resolution);
            case MULTI_AGENT -> executeMultiAgent(request.sessionId(), request.prompt(), request.tasks());
        };
    }

    public AgentResponse planOnly(String sessionId, String objective) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        session = session.addUserMessage(objective);

        PlanResponse plan = planningService.createPlan(objective);
        String planOutput = plan.summary() != null && !plan.summary().isBlank()
                ? plan.summary()
                : String.join("\n", plan.steps());
        session = session.addAssistantMessage(planOutput);
        sessionMemoryService.saveSession(session);
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
        Session session = sessionMemoryService.getOrCreate(sessionId);
        session = session.addUserMessage(prompt);
        AgentDefinition agentDefinition = requestedAgentId == null || requestedAgentId.isBlank()
                ? agentRegistryService.getDefaultChatAgent()
                : agentRegistryService.getEnabled(requestedAgentId).orElseGet(agentRegistryService::getDefaultChatAgent);
        ResolvedAgentRuntime runtime = agentRuntimeFactory.resolve(agentDefinition);
        String history = sessionMemoryService.summarizeHistory(session, 12);
        String languageDirective = ResponseLanguageHelper.responseDirective(prompt);
        String systemPrompt = """
                %s

                %s

                %s
                """.formatted(properties.getSystemPrompt(), runtime.systemPrompt(), languageDirective);
        String userMessage = """
                Conversation history:
                %s

                Current user request:
                %s
                """.formatted(history, prompt);
        String reply;
        try {
            reply = reactAgentExecutionSupport.execute(
                    agentDefinition,
                    runtime,
                    systemPrompt,
                    "Answer the user's latest request using the conversation history as context and available tools when helpful.",
                    userMessage
            );
        } catch (Exception ex) {
            reply = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .advisors(runtime.advisors())
                    .toolCallbacks(runtime.toolCallbacks())
                    .toolContext(runtime.toolContext())
                    .call()
                    .content();
        }
        session = session.addAssistantMessage(reply);
        sessionMemoryService.saveSession(session);
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

    /**
     * Execute using multi-agent mode.
     */
    public AgentResponse executeMultiAgent(String sessionId, String objective) {
        return executeMultiAgent(sessionId, objective, List.of());
    }

    /**
     * Execute using multi-agent mode with predefined tasks.
     */
    public AgentResponse executeMultiAgent(String sessionId, String objective, List<AgentTask> tasks) {
        if (!properties.getMultiAgent().isEnabled()) {
            return new AgentResponse("error", "Multi-agent execution is not enabled", List.of());
        }

        MultiAgentExecutionResponse response = tasks != null && !tasks.isEmpty()
                ? supervisorAgentService.executeWithTasks(sessionId, objective, tasks)
                : supervisorAgentService.execute(sessionId, objective);
        return convertToAgentResponse(response);
    }

    /**
     * Stream chat with SSE events.
     */
    public Flux<ServerSentEvent<SseEvent>> routeChatStream(AgentRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        String prompt = request.prompt();
        String agentId = request.agentId();
        AgentRequest.ExecutionMode mode = request.mode();


        return Flux.<SseEvent>create(emitter -> {
            try {
                // 1. Validate multi-agent is enabled
                if (request.isForceMultiAgent() && !properties.getMultiAgent().isEnabled()) {
                    emitter.next(SseEvent.error("Multi-agent execution is not enabled", null));
                    emitter.error(new IllegalStateException("Multi-agent execution is not enabled"));
                    return;
                }

                // 2. Emit session started event
                emitter.next(SseEvent.sessionStarted(sessionId));

                // 3. Resolve intent
                Session session = sessionMemoryService.getOrCreate(sessionId);
                IntentResolution resolution = intentResolutionService.resolve(prompt, session, request);

                // 4. Emit intent resolved event
                emitter.next(SseEvent.intentResolved(resolution));

                // 5. Execute based on route mode
                AgentResponse response;
                switch (resolution.routeMode()) {
                    case DIRECT_CHAT -> {
                        emitter.next(SseEvent.stepStarted(0, "Processing direct chat"));
                        response = chat(sessionId, prompt, firstNonBlank(agentId, resolution.preferredAgentId()));
                        emitter.next(SseEvent.stepCompleted(0, "Chat completed"));
                    }
                    case PLAN_EXECUTE -> {
                        response = executeWithPlanStream(emitter, sessionId, prompt, resolution);
                    }
                    case MULTI_AGENT -> {
                        response = executeMultiAgentStream(emitter, sessionId, prompt);
                    }
                    default -> {
                        response = chat(sessionId, prompt, agentId);
                    }
                }

                // 6. Emit completion event
                emitter.next(SseEvent.executionCompleted(resolution.routeMode().name(), response.summary(), response));
                emitter.complete();

            } catch (Exception e) {
                log.error("Error during streaming execution for session {}", sessionId, e.getMessage(), e);
                emitter.next(SseEvent.error("Execution failed: " + e.getMessage(), e));
                emitter.error(e);
            }
        }).map(event -> ServerSentEvent.<SseEvent>builder()
                .event(event.type().name().toLowerCase())
                .data(event)
                .build())
        .delayElements(Duration.ofMillis(10)); // Small delay to prevent overwhelming the client
    }


    private AgentResponse executeWithPlanStream(
            FluxSink<SseEvent> emitter,
            String sessionId,
            String objective,
            IntentResolution resolution
    ) {
        emitter.next(SseEvent.stepStarted(1, "Creating execution plan"));
        AgentResponse response = workflowService.executeAsAgentResponse(sessionId, objective, resolution);
        emitter.next(SseEvent.stepCompleted(1, "Plan execution completed"));
        return response;
    }

    private AgentResponse executeMultiAgentStream(
            FluxSink<SseEvent> emitter,
            String sessionId,
            String objective
    ) {
        if (!properties.getMultiAgent().isEnabled()) {
            emitter.next(SseEvent.error("Multi-agent execution is not enabled", null));
            return new AgentResponse("error", "Multi-agent execution is not enabled", List.of());
        }

        emitter.next(SseEvent.stepStarted(1, "Initializing multi-agent execution"));

        MultiAgentExecutionResponse multiResponse = supervisorAgentService.execute(sessionId, objective);

        // Emit events for each task
        int stepIndex = 2;
        for (var task : multiResponse.tasks()) {
            emitter.next(SseEvent.agentStarted(
                    task.assignedAgentId(),
                    task.taskId(),
                    task.goal()
            ));

            // Get task result if available
            String resultSummary = multiResponse.taskResults().get(task.taskId()) != null
                    ? multiResponse.taskResults().get(task.taskId()).toString()
                    : "Task completed";
            emitter.next(SseEvent.taskCompleted(task.taskId(), resultSummary));
            stepIndex++;
        }

        emitter.next(SseEvent.stepCompleted(1, "Multi-agent execution completed"));

        // Convert to AgentResponse
        return convertToAgentResponse(multiResponse);
    }

    private AgentResponse convertToAgentResponse(MultiAgentExecutionResponse multiResponse) {
        List<String> steps = multiResponse.tasks().stream()
                .map(task -> "- " + task.goal())
                .toList();

        List<String> artifacts = multiResponse.taskResults().values().stream()
                .map(Object::toString)
                .toList();

        return new AgentResponse(
                "multi-agent",
                multiResponse.objective(),
                multiResponse.aggregatedOutput(),
                formatMultiAgentOutput(multiResponse),
                steps,
                List.of(),
                artifacts,
                List.of(),
                null,
                null,
                null
        );
    }

    private String formatMultiAgentOutput(MultiAgentExecutionResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Multi-Agent Execution Result\n\n");
        sb.append("**Objective:** ").append(response.objective()).append("\n\n");
        sb.append("**Status:** ").append(response.status().name()).append("\n\n");

        if (response.statusSummary() != null && !response.statusSummary().isEmpty()) {
            sb.append("**Summary:** \n");
            for (Map.Entry<AgentTaskStatus, Long> entry : response.statusSummary().entrySet()) {
                sb.append("- ").append(entry.getKey().name()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        if (response.aggregatedOutput() != null && !response.aggregatedOutput().isBlank()) {
            sb.append("\n### Results\n\n");
            sb.append(response.aggregatedOutput());
        }

        if (response.error() != null && !response.error().isBlank()) {
            sb.append("\n### Error\n\n");
            sb.append(response.error());
        }

        return sb.toString();
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