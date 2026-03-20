package com.openmanus.saa.controller;

import com.openmanus.saa.model.AgentRequest;
import com.openmanus.saa.model.AgentResponse;
import com.openmanus.saa.model.PlanResponse;
import com.openmanus.saa.service.ManusAgentService;
import com.openmanus.saa.service.PlanningService;
import com.openmanus.saa.service.WorkflowService;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ManusAgentService manusAgentService;
    private final PlanningService planningService;
    private final WorkflowService workflowService;

    public AgentController(
            ManusAgentService manusAgentService,
            PlanningService planningService,
            WorkflowService workflowService
    ) {
        this.manusAgentService = manusAgentService;
        this.planningService = planningService;
        this.workflowService = workflowService;
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
}
