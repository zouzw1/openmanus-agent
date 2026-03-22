package com.openmanus.saa.service.mcp;

import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.model.mcp.McpServerStatus;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class McpPromptContextService {

    private static final Logger log = LoggerFactory.getLogger(McpPromptContextService.class);

    private final McpProperties properties;
    private final McpService mcpService;

    public McpPromptContextService(McpProperties properties, McpService mcpService) {
        this.properties = properties;
        this.mcpService = mcpService;
    }

    public String describeAvailableTools() {
        return describeAvailableTools(true, null, null);
    }

    public String describeAvailableTools(boolean allowAll, java.util.Set<String> allowedServers, java.util.Set<String> allowedTools) {
        if (!properties.isEnabled()) {
            String description = """
                    MCP status:
                    - MCP is disabled.
                    """;
            log.debug("MCP Tools Description (disabled): {}", description);
            return description;
        }

        List<McpServerStatus> servers = mcpService.listServers().stream()
                .filter(server -> isServerVisible(server, allowAll, allowedServers, allowedTools))
                .map(server -> filterServerTools(server, allowAll, allowedServers, allowedTools))
                .filter(server -> server.tools() == null || !server.tools().isEmpty() || (allowedServers != null && allowedServers.contains(server.serverId())))
                .toList();
        if (servers.isEmpty()) {
            String description = """
                    MCP status:
                    - MCP is enabled, but no allowed MCP servers or tools are currently available.
                    """;
            log.debug("MCP Tools Description (no servers): {}", description);
            return description;
        }

        String descriptions = servers.stream()
                .map(this::describeServer)
                .collect(Collectors.joining("\n"));
        
        String result = """
                MCP status:
                %s

                If you need an MCP capability, call the MCP bridge tool with the exact serverId, toolName, and JSON arguments.
                Only use tools listed below.
                """.formatted(descriptions);
        
        log.info("=== Available MCP Tools for LLM ===");
        log.info("{}", result);
        log.info("=====================================");
        
        return result;
    }

    private String describeServer(McpServerStatus server) {
        Map<String, McpToolMetadata> metadataByName = mcpService.listToolMetadata().stream()
                .filter(tool -> server.serverId().equals(tool.serverId()))
                .collect(Collectors.toMap(
                        McpToolMetadata::name,
                        tool -> tool,
                        (left, right) -> left
                ));

        StringBuilder builder = new StringBuilder();
        builder.append("- serverId=")
                .append(server.serverId())
                .append(", type=")
                .append(server.type())
                .append(", connected=")
                .append(server.connected())
                .append("\n");

        if (server.tools() == null || server.tools().isEmpty()) {
            builder.append("  - (no tools reported)");
            return builder.toString();
        }

        for (String toolName : server.tools()) {
            McpToolMetadata metadata = metadataByName.get(toolName);
            if (metadata == null) {
                builder.append("  - tool=").append(toolName).append("\n");
                continue;
            }
            builder.append(metadata.toPromptGuidance());
        }
        return builder.toString().trim();
    }

    private boolean isServerVisible(McpServerStatus server, boolean allowAll, java.util.Set<String> allowedServers, java.util.Set<String> allowedTools) {
        if (allowAll) {
            return true;
        }
        if (allowedServers != null && allowedServers.contains(server.serverId())) {
            return true;
        }
        if (allowedTools == null || allowedTools.isEmpty()) {
            return false;
        }
        String prefix = server.serverId() + "/";
        return allowedTools.stream().anyMatch(tool -> tool.startsWith(prefix));
    }

    private McpServerStatus filterServerTools(
            McpServerStatus server,
            boolean allowAll,
            java.util.Set<String> allowedServers,
            java.util.Set<String> allowedTools
    ) {
        if (allowAll || (allowedServers != null && allowedServers.contains(server.serverId()))) {
            return server;
        }
        List<String> filteredTools = server.tools() == null
                ? List.of()
                : server.tools().stream()
                        .filter(tool -> allowedTools != null && allowedTools.contains(server.serverId() + "/" + tool))
                        .toList();
        return new McpServerStatus(server.serverId(), server.type(), server.connected(), server.endpoint(), filteredTools);
    }
}
