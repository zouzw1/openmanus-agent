package com.openmanus.saa.prompt.section;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.CapabilityAccessMode;
import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.agent.McpAccessPolicy;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import com.openmanus.saa.service.ToolRegistryService;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * MCP 工具描述 Section。
 *
 * <p>渲染 Agent 可用的 MCP 工具列表。
 */
@Component
public class McpToolsPromptSection implements PromptSection {

    private final ToolRegistryService toolRegistryService;
    private final McpPromptContextService mcpPromptContextService;

    public McpToolsPromptSection(
            ToolRegistryService toolRegistryService,
            McpPromptContextService mcpPromptContextService) {
        this.toolRegistryService = toolRegistryService;
        this.mcpPromptContextService = mcpPromptContextService;
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public String title() {
        return "## MCP Tools";
    }

    @Override
    public String render(PromptContext context) {
        AgentDefinition agent = context.agent();
        McpAccessPolicy mcp = agent.getMcp();

        // 检查是否启用 callMcpTool
        Set<String> knownToolNames = toolRegistryService.getEnabledTools().stream()
            .map(ToolMetadata::getName)
            .collect(Collectors.toSet());
        IdAccessPolicy localTools = agent.getLocalTools();
        Set<String> allowedLocalTools = localTools.resolveAllowed(knownToolNames);

        if (!allowedLocalTools.contains("callMcpTool") || mcp.isDenied()) {
            return "MCP tools are not enabled for this agent.";
        }

        return mcpPromptContextService.describeAvailableTools(
            mcp.getMode() == CapabilityAccessMode.ALLOW_ALL,
            mcp.getServers(),
            mcp.getTools()
        );
    }
}
