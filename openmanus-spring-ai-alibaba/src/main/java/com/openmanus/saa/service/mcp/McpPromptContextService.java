package com.openmanus.saa.service.mcp;

import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.model.mcp.McpServerStatus;
import java.util.List;
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
        if (!properties.isEnabled()) {
            String description = """
                    MCP status:
                    - MCP is disabled.
                    """;
            log.debug("MCP Tools Description (disabled): {}", description);
            return description;
        }

        List<McpServerStatus> servers = mcpService.listServers();
        if (servers.isEmpty()) {
            String description = """
                    MCP status:
                    - MCP is enabled, but no MCP servers are connected.
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
        String tools = server.tools() == null || server.tools().isEmpty()
                ? "(no tools reported)"
                : String.join(", ", server.tools());
        return "- serverId=%s, type=%s, connected=%s, tools=%s"
                .formatted(server.serverId(), server.type(), server.connected(), tools);
    }
}
