package com.openmanus.saa.controller;

import com.openmanus.saa.model.AgentTask;
import com.openmanus.saa.service.supervisor.MultiAgentExecutionResponse;
import com.openmanus.saa.service.supervisor.SupervisorAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for multi-agent execution.
 * Provides endpoints for executing tasks with multiple agents in parallel.
 */
@RestController
@RequestMapping("/api/multi-agent")
@CrossOrigin(origins = "*")
public class MultiAgentController {

    private final SupervisorAgentService supervisorAgentService;

    public MultiAgentController(SupervisorAgentService supervisorAgentService) {
        this.supervisorAgentService = supervisorAgentService;
    }

    /**
     * Execute a multi-agent task with automatic decomposition.
     *
     * @param request the execution request
     * @return the execution response
     */
    @PostMapping("/execute")
    public ResponseEntity<MultiAgentExecutionResponse> execute(@RequestBody MultiAgentRequest request) {
        MultiAgentExecutionResponse response = supervisorAgentService.execute(
                request.sessionId(),
                request.objective()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Execute a multi-agent task with pre-defined tasks.
     *
     * @param request the execution request with tasks
     * @return the execution response
     */
    @PostMapping("/execute-with-tasks")
    public ResponseEntity<MultiAgentExecutionResponse> executeWithTasks(@RequestBody MultiAgentRequestWithTasks request) {
        MultiAgentExecutionResponse response = supervisorAgentService.executeWithTasks(
                request.sessionId(),
                request.objective(),
                request.tasks()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Request body for multi-agent execution.
     */
    public record MultiAgentRequest(
            String sessionId,
            String objective
    ) {
    }

    /**
     * Request body for multi-agent execution with pre-defined tasks.
     */
    public record MultiAgentRequestWithTasks(
            String sessionId,
            String objective,
            List<AgentTask> tasks
    ) {
    }
}
