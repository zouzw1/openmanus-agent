package com.openmanus.saa.controller;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.HumanFeedbackResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import com.openmanus.saa.model.WorkflowFeedbackRequest;
import com.openmanus.saa.service.HumanFeedbackResolutionService;
import com.openmanus.saa.service.ManusAgentService;
import com.openmanus.saa.service.PlanningService;
import com.openmanus.saa.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ManusAgentService manusAgentService;
    private final PlanningService planningService;
    private final WorkflowService workflowService;
    private final HumanFeedbackResolutionService humanFeedbackResolutionService;

    public AgentController(
            ManusAgentService manusAgentService,
            PlanningService planningService,
            WorkflowService workflowService,
            HumanFeedbackResolutionService humanFeedbackResolutionService
    ) {
        this.manusAgentService = manusAgentService;
        this.planningService = planningService;
        this.workflowService = workflowService;
        this.humanFeedbackResolutionService = humanFeedbackResolutionService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        return manusAgentService.chat(request.sessionId(), request.prompt());
    }

    @PostMapping("/plan")
    public PlanResponse plan(@Valid @RequestBody AgentRequest request) {
        return planningService.createPlan(request.prompt());
    }

    @PostMapping("/execute")
    public AgentResponse execute(@Valid @RequestBody AgentRequest request) {
        return manusAgentService.executeWithPlan(request.sessionId(), request.prompt());
    }

    @PostMapping("/workflow/execute")
    public WorkflowExecutionResponse executeWorkflow(@Valid @RequestBody AgentRequest request) {
        return workflowService.execute(request.sessionId(), request.prompt());
    }

    @GetMapping("/workflow/pending-feedback/{sessionId}")
    public ResponseEntity<HumanFeedbackRequest> getPendingWorkflowFeedback(@PathVariable String sessionId) {
        return workflowService.getPendingFeedback(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/workflow/feedback")
    public WorkflowExecutionResponse submitWorkflowFeedback(@Valid @RequestBody WorkflowFeedbackRequest request) {
        try {
            HumanFeedbackRequest pendingFeedback = workflowService.getPendingFeedback(request.sessionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No pending workflow feedback found for session: " + request.sessionId()
                    ));
            HumanFeedbackResponse feedback = humanFeedbackResolutionService.resolve(request, pendingFeedback);
            return workflowService.submitHumanFeedback(request.sessionId(), feedback);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
