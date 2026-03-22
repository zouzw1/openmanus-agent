package com.openmanus.saa.service;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.model.AgentCapabilitySnapshot;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.mcp.McpServerStatus;
import com.openmanus.saa.service.mcp.McpService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AgentCapabilitySnapshotService {

    private final AgentRegistryService agentRegistryService;
    private final ToolRegistryService toolRegistryService;
    private final SkillsService skillsService;
    private final McpService mcpService;

    public AgentCapabilitySnapshotService(
            AgentRegistryService agentRegistryService,
            ToolRegistryService toolRegistryService,
            SkillsService skillsService,
            McpService mcpService
    ) {
        this.agentRegistryService = agentRegistryService;
        this.toolRegistryService = toolRegistryService;
        this.skillsService = skillsService;
        this.mcpService = mcpService;
    }

    public List<AgentCapabilitySnapshot> listPlanningVisibleSnapshots(boolean includeDataAnalysisAgent) {
        return agentRegistryService.listPlanningVisible().stream()
                .filter(agent -> includeDataAnalysisAgent || !"data_analysis".equals(agent.getId()))
                .map(this::buildSnapshot)
                .toList();
    }

    private AgentCapabilitySnapshot buildSnapshot(AgentDefinition agentDefinition) {
        Set<String> knownLocalToolNames = toolRegistryService.getEnabledTools().stream()
                .map(ToolMetadata::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> localTools = agentDefinition.getLocalTools().resolveAllowed(knownLocalToolNames).stream()
                .sorted()
                .toList();

        Set<String> knownSkillNames = skillsService.isEnabled()
                ? skillsService.listSkills().stream().map(skill -> skill.name()).collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        List<String> skills = agentDefinition.getSkills().resolveAllowed(knownSkillNames).stream()
                .sorted()
                .toList();

        List<String> mcpTools = resolveAllowedMcpTools(agentDefinition);

        return new AgentCapabilitySnapshot(
                agentDefinition.getId(),
                agentDefinition.getExecutorType(),
                agentDefinition.getDescription(),
                localTools,
                mcpTools,
                skills
        );
    }

    private List<String> resolveAllowedMcpTools(AgentDefinition agentDefinition) {
        if (agentDefinition.getMcp().isDenied()) {
            return List.of();
        }
        return mcpService.listServers().stream()
                .flatMap(server -> resolveAllowedToolsForServer(agentDefinition, server).stream())
                .sorted()
                .toList();
    }

    private List<String> resolveAllowedToolsForServer(AgentDefinition agentDefinition, McpServerStatus server) {
        if (!server.connected() || server.tools() == null || server.tools().isEmpty()) {
            return List.of();
        }
        return server.tools().stream()
                .filter(toolName -> agentDefinition.getMcp().allows(server.serverId(), toolName))
                .map(toolName -> server.serverId() + "/" + toolName)
                .toList();
    }
}
