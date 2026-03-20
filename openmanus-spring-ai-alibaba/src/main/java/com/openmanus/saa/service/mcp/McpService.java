package com.openmanus.saa.service.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.model.mcp.McpConnectRequest;
import com.openmanus.saa.model.mcp.McpServerStatus;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private final McpProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

    public McpService(McpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initialize() {
        initializeConfiguredServers();
    }

    public List<McpServerStatus> listServers() {
        List<McpServerStatus> statuses = new ArrayList<>();
        for (Map.Entry<String, McpConnection> entry : connections.entrySet()) {
            McpConnection connection = entry.getValue();
            statuses.add(new McpServerStatus(
                    connection.serverId(),
                    connection.type(),
                    connection.connected(),
                    connection.endpoint(),
                    connection.listTools()
            ));
        }
        return statuses;
    }

    public McpServerStatus connect(McpConnectRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("MCP is disabled. Set openmanus.mcp.enabled=true.");
        }
        McpConnection connection = createConnection(
                request.serverId(),
                request.type(),
                request.url(),
                request.sseEndpoint(),
                request.command(),
                request.args()
        );
        connections.put(request.serverId(), connection);
        return new McpServerStatus(
                connection.serverId(),
                connection.type(),
                connection.connected(),
                connection.endpoint(),
                connection.listTools()
        );
    }

    public McpToolCallResult invoke(String serverId, String toolName, String argumentsJson) {
        log.debug("Invoking MCP tool: serverId={}, toolName={}", serverId, toolName);
        
        McpConnection connection = connections.get(serverId);
        if (connection == null) {
            log.warn("MCP server not connected: {}", serverId);
            return new McpToolCallResult(serverId, toolName, false, "MCP server not connected: " + serverId);
        }
        
        McpToolCallResult result = connection.invoke(toolName, argumentsJson);
        
        log.debug("MCP tool invocation completed: serverId={}, toolName={}, success={}", 
                serverId, toolName, result.success());
        
        return result;
    }

    public boolean disconnect(String serverId) {
        McpConnection connection = connections.remove(serverId);
        if (connection == null) {
            return false;
        }
        connection.close();
        return true;
    }

    private void initializeConfiguredServers() {
        if (!properties.isEnabled()) {
            log.info("MCP is disabled, skipping server initialization");
            return;
        }
        log.info("Initializing {} MCP servers", properties.getServers().size());
        for (Map.Entry<String, McpProperties.ServerConfig> entry : properties.getServers().entrySet()) {
            McpProperties.ServerConfig config = entry.getValue();
            try {
                log.info("Connecting to MCP server '{}' (type: {}, url: {})", 
                        entry.getKey(), config.getType(), config.getUrl());
                connections.put(entry.getKey(), createConnection(
                        entry.getKey(),
                        config.getType(),
                        config.getUrl(),
                        config.getSseEndpoint(),
                        config.getCommand(),
                        config.getArgs()
                ));
                log.info("Successfully connected to MCP server '{}'", entry.getKey());
            } catch (Exception ex) {
                log.warn("Skipping MCP server '{}' during startup: {}", entry.getKey(), rootMessage(ex), ex);
            }
        }
    }

    private McpConnection createConnection(
            String serverId,
            String type,
            String url,
            String sseEndpoint,
            String command,
            List<String> args
    ) {
        if ("stdio".equalsIgnoreCase(type)) {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("stdio MCP server requires command");
            }
            return new StdioMcpConnection(serverId, command, args);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sse MCP server requires url");
        }
        return new SseMcpConnection(serverId, url, sseEndpoint, objectMapper);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        for (McpConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
    }
}
