package com.openmanus.saa.controller;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.SseEvent;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.service.HumanFeedbackResolutionService;
import com.openmanus.saa.service.ManusAgentService;
import com.openmanus.saa.service.PlanningService;
import com.openmanus.saa.service.WorkflowService;
import com.openmanus.saa.service.context.ConversationContextFactory;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ManusAgentService manusAgentService;
    private final PlanningService planningService;
    private final WorkflowService workflowService;
    private final HumanFeedbackResolutionService humanFeedbackResolutionService;
    private final ConversationContextFactory contextFactory;

    public AgentController(
            ManusAgentService manusAgentService,
            PlanningService planningService,
            WorkflowService workflowService,
            HumanFeedbackResolutionService humanFeedbackResolutionService,
            ConversationContextFactory contextFactory
    ) {
        this.manusAgentService = manusAgentService;
        this.planningService = planningService;
        this.workflowService = workflowService;
        this.humanFeedbackResolutionService = humanFeedbackResolutionService;
        this.contextFactory = contextFactory;
    }

    /**
     * Unified chat endpoint with automatic routing.
     * Supports mode control via AgentRequest.mode:
     * - AUTO: Auto-detect execution mode (default)
     * - SINGLE: Force single agent execution
     * - MULTI: Force multi-agent execution
     *
     * Also supports predefined tasks for multi-agent mode via AgentRequest.tasks.
     */
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        // 构建对话上下文
        ConversationContext context = contextFactory.create(request.sessionId(), request.prompt());

        // 检查是否有暂停的工作流需要恢复
        if (context.hasPausedWorkflow()) {
            log.info(
                "Routing /api/agent/chat request as workflow feedback for session {}",
                request.sessionId()
            );
            HumanFeedbackRequest pendingFeedback = context.workflowState().pendingFeedback();
            WorkflowFeedbackRequest feedbackRequest = new WorkflowFeedbackRequest(
                request.sessionId(),
                null,
                request.prompt(),
                null,
                null
            );
            HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(feedbackRequest, pendingFeedback);
            return workflowService.submitHumanFeedbackAsAgentResponse(request.sessionId(), feedback);
        }

        log.info(
            "Routing /api/agent/chat request with mode {} for session {}",
            request.mode(),
            request.sessionId()
        );

        // Use unified routing with mode control
        return manusAgentService.routeChat(request);
    }

    @PostMapping("/plan")
    public PlanResponse plan(@Valid @RequestBody AgentRequest request) {
        return planningService.createPlan(request.prompt());
    }

    @PostMapping("/execute")
    public AgentResponse execute(@Valid @RequestBody AgentRequest request) {
        return workflowService.executeAsAgentResponse(request.sessionId(), request.prompt());
    }

    /**
     * Stream chat with SSE events.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SseEvent>> chatStream(@Valid @RequestBody AgentRequest request) {
        log.info(
            "Received SSE stream request for session {}",
            request.sessionId() != null ? request.sessionId() : "new"
        );
        return manusAgentService.routeChatStream(request);
    }

    @GetMapping({"/pending-feedback/{sessionId}", "/workflow/pending-feedback/{sessionId}"})
    public ResponseEntity<HumanFeedbackRequest> getPendingWorkflowFeedback(@PathVariable String sessionId) {
        return workflowService.getPendingFeedback(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping({"/feedback", "/workflow/feedback"})
    public AgentResponse submitWorkflowFeedback(@Valid @RequestBody WorkflowFeedbackRequest request) {
        try {
            HumanFeedbackRequest pendingFeedback = workflowService.getPendingFeedback(request.sessionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No pending workflow feedback found for session: " + request.sessionId()
                ));
            HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(request, pendingFeedback);
            return workflowService.submitHumanFeedbackAsAgentResponse(request.sessionId(), feedback);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}