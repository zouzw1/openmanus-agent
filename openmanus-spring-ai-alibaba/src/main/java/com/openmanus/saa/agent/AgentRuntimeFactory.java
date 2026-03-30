package com.openmanus.saa.agent;

import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.model.SkillInfoResponse;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.rag.advisor.RagRetrievalAdvisor;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.service.SkillsService;
import com.openmanus.saa.service.ToolRegistryService;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.service.mcp.McpService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeFactory {

    private final ToolRegistryService toolRegistryService;
    private final LocalToolCallbackCatalog localToolCallbackCatalog;
    private final McpPromptContextService mcpPromptContextService;
    private final McpService mcpService;
    private final SkillsService skillsService;
    private final ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider;
    private final ObjectProvider<RagProperties> ragPropertiesProvider;

    public AgentRuntimeFactory(
            ToolRegistryService toolRegistryService,
            LocalToolCallbackCatalog localToolCallbackCatalog,
            McpPromptContextService mcpPromptContextService,
            McpService mcpService,
            SkillsService skillsService,
            ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider,
            ObjectProvider<RagProperties> ragPropertiesProvider
    ) {
        this.toolRegistryService = toolRegistryService;
        this.localToolCallbackCatalog = localToolCallbackCatalog;
        this.mcpPromptContextService = mcpPromptContextService;
        this.mcpService = mcpService;
        this.skillsService = skillsService;
        this.ragRetrievalServiceProvider = ragRetrievalServiceProvider;
        this.ragPropertiesProvider = ragPropertiesProvider;
    }

    public ResolvedAgentRuntime resolve(AgentDefinition agentDefinition) {
        return resolve(agentDefinition, null, null, null, null, null);
    }

    public ResolvedAgentRuntime resolveForStep(
            AgentDefinition agentDefinition,
            WorkflowStep step,
            List<String> toolInvocations,
            List<String> toolInvocationDetails,
            List<String> toolOutputs
    ) {
        Collection<String> stepToolNames = step == null ? null : step.getRequiredTools();
        Set<String> allowedStepTools = stepToolNames == null
                ? Set.of()
                : stepToolNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        String declaredSkillName = extractDeclaredSkillName(step);
        return resolve(agentDefinition, allowedStepTools, declaredSkillName, toolInvocations, toolInvocationDetails, toolOutputs);
    }

    private ResolvedAgentRuntime resolve(
            AgentDefinition agentDefinition,
            Set<String> stepToolNames,
            String declaredSkillName,
            List<String> toolInvocations,
            List<String> toolInvocationDetails,
            List<String> toolOutputs
    ) {
        Set<String> knownLocalToolNames = toolRegistryService.getEnabledTools().stream()
                .map(ToolMetadata::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowedLocalToolNames = agentDefinition.getLocalTools().resolveAllowed(knownLocalToolNames);
        if (agentDefinition.getRag().usesTools()) {
            allowedLocalToolNames = new LinkedHashSet<>(allowedLocalToolNames);
            if (knownLocalToolNames.contains("rag_ingest")) {
                allowedLocalToolNames.add("rag_ingest");
            }
            if (knownLocalToolNames.contains("rag_search")) {
                allowedLocalToolNames.add("rag_search");
            }
        }
        boolean skillScopedStep = declaredSkillName != null && !declaredSkillName.isBlank();
        if (stepToolNames != null && !skillScopedStep) {
            allowedLocalToolNames = allowedLocalToolNames.stream()
                    .filter(stepToolNames::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<ToolCallback> callbacks = new ArrayList<>(localToolCallbackCatalog.getCallbacks(allowedLocalToolNames));
        if (allowedLocalToolNames.contains("callMcpTool")
                && !agentDefinition.getMcp().isDenied()
                && (stepToolNames == null || stepToolNames.isEmpty() || stepToolNames.contains("callMcpTool") || !skillScopedStep)) {
            callbacks.add(new AuthorizedMcpToolCallback(mcpService, agentDefinition.getMcp()));
        }

        Set<String> availableSkillNames = resolveAvailableSkillNames();
        Set<String> allowedSkillNames = agentDefinition.getSkills().resolveAllowed(availableSkillNames);
        if (skillScopedStep) {
            allowedSkillNames = allowedSkillNames.stream()
                    .filter(skillName -> skillName.equals(declaredSkillName))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else if (stepToolNames != null && !stepToolNames.contains("read_skill")) {
            allowedSkillNames = Set.of();
        }
        if (!agentDefinition.getSkills().isDenied() && !allowedSkillNames.isEmpty()) {
            callbacks.add(new AuthorizedSkillToolCallback(skillsService, new IdAccessPolicy(CapabilityAccessMode.ALLOW_LIST, allowedSkillNames)));
        }
        if (toolInvocations != null) {
            callbacks = callbacks.stream()
                    .map(callback -> (ToolCallback) new TrackingToolCallback(callback, toolInvocations, toolInvocationDetails, toolOutputs))
                    .toList();
        }

        List<Advisor> advisors = resolveAdvisors(agentDefinition);

        Map<String, Object> toolContext = new LinkedHashMap<>();
        toolContext.put("agentId", agentDefinition.getId());
        if (!agentDefinition.getRag().getKnowledgeBaseIds().isEmpty()) {
            toolContext.put("knowledgeBaseIds", List.copyOf(agentDefinition.getRag().getKnowledgeBaseIds()));
        }

        return new ResolvedAgentRuntime(
                agentDefinition,
                buildSystemPrompt(agentDefinition, allowedLocalToolNames, allowedSkillNames),
                List.copyOf(callbacks),
                List.copyOf(advisors),
                Map.copyOf(toolContext)
        );
    }

    private String extractDeclaredSkillName(WorkflowStep step) {
        if (step == null || step.getParameterContext() == null) {
            return null;
        }
        Object skillNameValue = step.getParameterContext().get("skillName");
        if (!(skillNameValue instanceof String skillName) || skillName.isBlank()) {
            return null;
        }
        return skillName.trim();
    }

    private String buildSystemPrompt(AgentDefinition agentDefinition, Set<String> allowedLocalToolNames, Set<String> allowedSkillNames) {
        StringBuilder builder = new StringBuilder();
        if (agentDefinition.getSystemPrompt() != null && !agentDefinition.getSystemPrompt().isBlank()) {
            builder.append(agentDefinition.getSystemPrompt().trim()).append("\n\n");
        }

        builder.append("Agent profile:\n")
                .append("- Agent ID: ").append(agentDefinition.getId()).append("\n")
                .append("- Agent name: ").append(agentDefinition.getName()).append("\n")
                .append("- Executor type: ").append(agentDefinition.getExecutorType()).append("\n")
                .append("- Description: ").append(agentDefinition.getDescription()).append("\n\n");

        builder.append(toolRegistryService.generateToolsPromptGuidance(allowedLocalToolNames)).append("\n\n");
        if (allowedLocalToolNames.contains("callMcpTool")) {
            builder.append(mcpPromptContextService.describeAvailableTools(
                    agentDefinition.getMcp().getMode() == CapabilityAccessMode.ALLOW_ALL,
                    agentDefinition.getMcp().getServers(),
                    agentDefinition.getMcp().getTools()
            ));
        } else {
            builder.append("MCP tools are not enabled for this runtime.");
        }

        builder.append("\n\nSkills status:\n");
        if (allowedSkillNames.isEmpty()) {
            builder.append("- No skills are enabled for this agent.");
        } else {
            builder.append("- Allowed skills are accessible only via the read_skill tool.\n");
            for (SkillInfoResponse skill : resolveAllowedSkills(allowedSkillNames)) {
                builder.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n");
            }
        }

        builder.append("\n\nRAG status:\n");
        if (!agentDefinition.getRag().isEnabled()) {
            builder.append("- RAG is disabled for this agent.");
        } else {
            builder.append("- Mode: ").append(agentDefinition.getRag().getMode()).append("\n");
            if (agentDefinition.getRag().getKnowledgeBaseIds().isEmpty()) {
                builder.append("- Knowledge bases: none preconfigured\n");
            } else {
                builder.append("- Knowledge bases: ")
                        .append(String.join(", ", agentDefinition.getRag().getKnowledgeBaseIds()))
                        .append("\n");
            }
            builder.append("- RAG tools exposed: ").append(agentDefinition.getRag().usesTools() ? "yes" : "no").append("\n");
            builder.append("- RAG advisor enabled: ").append(agentDefinition.getRag().usesAdvisor() ? "yes" : "no");
        }

        return builder.toString().trim();
    }

    private List<Advisor> resolveAdvisors(AgentDefinition agentDefinition) {
        if (!agentDefinition.getRag().usesAdvisor() || agentDefinition.getRag().getKnowledgeBaseIds().isEmpty()) {
            return List.of();
        }
        RagRetrievalService ragRetrievalService = ragRetrievalServiceProvider.getIfAvailable();
        RagProperties ragProperties = ragPropertiesProvider.getIfAvailable();
        if (ragRetrievalService == null || ragProperties == null) {
            return List.of();
        }
        return List.of(new RagRetrievalAdvisor(
                agentDefinition.getId(),
                agentDefinition.getRag(),
                ragRetrievalService,
                ragProperties
        ));
    }

    private Set<String> resolveAvailableSkillNames() {
        if (!skillsService.isEnabled()) {
            return Set.of();
        }
        return skillsService.listSkills().stream()
                .map(SkillInfoResponse::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<SkillInfoResponse> resolveAllowedSkills(Set<String> allowedSkillNames) {
        if (!skillsService.isEnabled() || allowedSkillNames.isEmpty()) {
            return List.of();
        }
        return skillsService.listSkills().stream()
                .filter(skill -> allowedSkillNames.contains(skill.name()))
                .toList();
    }
}
