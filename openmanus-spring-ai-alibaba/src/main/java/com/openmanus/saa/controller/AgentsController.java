package com.openmanus.saa.controller;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.model.AgentDefinitionResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentsController {

    private final AgentRegistryService agentRegistryService;

    public AgentsController(AgentRegistryService agentRegistryService) {
        this.agentRegistryService = agentRegistryService;
    }

    @GetMapping
    public List<AgentDefinitionResponse> listAgents() {
        return agentRegistryService.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinitionResponse> getAgent(@PathVariable String agentId) {
        return agentRegistryService.get(agentId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        List<AgentDefinitionResponse> agents = agentRegistryService.reload().stream()
                .map(this::toResponse)
                .toList();
        return Map.of(
                "success", true,
                "count", agents.size(),
                "agents", agents
        );
    }

    private AgentDefinitionResponse toResponse(AgentDefinition definition) {
        return new AgentDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.isEnabled(),
                definition.getExecutorType(),
                definition.getDescription(),
                definition.isPlanningVisible(),
                definition.getPriority(),
                definition.getPromptFile(),
                definition.getLocalTools().getIds(),
                definition.getMcp().getServers(),
                definition.getMcp().getTools(),
                definition.getSkills().getIds()
        );
    }
}
