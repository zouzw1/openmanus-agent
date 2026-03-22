package com.openmanus.saa.service.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import com.openmanus.saa.model.ToolMetadata.ParameterSchema;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseMcpConnection implements McpConnection {

    private static final Logger log = LoggerFactory.getLogger(SseMcpConnection.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String serverId;
    private final String endpoint;
    private final McpSyncClient client;
    private final ObjectMapper objectMapper;
    private volatile boolean initialized;
    private volatile String lastError;

    public SseMcpConnection(String serverId, String baseUrl, String sseEndpoint, ObjectMapper objectMapper) {
        this.serverId = serverId;
        this.objectMapper = objectMapper;
        String resolvedSseEndpoint = (sseEndpoint == null || sseEndpoint.isBlank()) ? "/sse" : sseEndpoint;
        this.endpoint = baseUrl + resolvedSseEndpoint;

        // Remove trailing slash from base URL to avoid double slashes when building endpoint
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        log.info("Creating MCP SSE connection to server '{}' at {}", serverId, endpoint);
        
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(cleanBaseUrl)
                .sseEndpoint(resolvedSseEndpoint.startsWith("/") ? resolvedSseEndpoint.substring(1) : resolvedSseEndpoint)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        this.client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(REQUEST_TIMEOUT)
                .clientInfo(new Implementation("openmanus-spring-ai-alibaba", "OpenManus Spring AI Alibaba", "0.1.0-SNAPSHOT"))
                .build();
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public String type() {
        return "sse";
    }

    @Override
    public String endpoint() {
        return endpoint;
    }

    @Override
    public boolean connected() {
        return initialized && client.isInitialized();
    }

    @Override
    public List<String> listTools() {
        return listToolMetadata().stream()
                .map(McpToolMetadata::name)
                .toList();
    }

    @Override
    public List<McpToolMetadata> listToolMetadata() {
        try {
            ensureInitialized();
            ListToolsResult result = client.listTools();
            return result.tools().stream()
                    .map(this::toToolMetadata)
                    .toList();
        } catch (Exception ex) {
            return List.of(new McpToolMetadata(serverId, "unavailable", messageFor(ex), List.of()));
        }
    }

    @Override
    public McpToolCallResult invoke(String toolName, String argumentsJson) {
        try {
            ensureInitialized();
            Map<String, Object> arguments = parseArguments(argumentsJson);
            CallToolResult result = client.callTool(new CallToolRequest(toolName, arguments));
            String output = renderToolResult(result);
            boolean success = !Boolean.TRUE.equals(result.isError());
            return new McpToolCallResult(serverId, toolName, success, output);
        } catch (Exception ex) {
            return new McpToolCallResult(serverId, toolName, false, messageFor(ex));
        }
    }

    @Override
    public void close() {
        if (initialized) {
            client.closeGracefully();
        }
    }

    private synchronized void ensureInitialized() {
        if (initialized && client.isInitialized()) {
            return;
        }
        try {
            log.debug("Initializing MCP connection to server '{}' at {}", serverId, endpoint);
            client.initialize();
            initialized = true;
            lastError = null;
            log.info("Successfully connected to MCP server '{}' at {}", serverId, endpoint);
        } catch (Exception ex) {
            initialized = false;
            lastError = rootMessage(ex);
            log.error("Failed to initialize MCP server '{}' at {}: {}", serverId, endpoint, lastError, ex);
            throw new IllegalStateException(
                    "Failed to initialize MCP server '%s' at %s: %s".formatted(serverId, endpoint, lastError),
                    ex
            );
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) throws Exception {
        String safeArgs = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
        return objectMapper.readValue(safeArgs, new TypeReference<>() {
        });
    }

    private McpToolMetadata toToolMetadata(Tool tool) {
        return new McpToolMetadata(
                serverId,
                tool.name(),
                tool.description(),
                extractParameterSchemas(tool)
        );
    }

    private List<ParameterSchema> extractParameterSchemas(Tool tool) {
        JsonNode schemaNode = normalizeSchemaNode(tool.inputSchema());
        if (schemaNode == null || !schemaNode.isObject()) {
            return List.of();
        }

        JsonNode propertiesNode = schemaNode.path("properties");
        if (!propertiesNode.isObject()) {
            return List.of();
        }

        Set<String> requiredNames = objectMapper.convertValue(
                schemaNode.path("required"),
                new TypeReference<Set<String>>() { }
        );

        List<ParameterSchema> parameters = new java.util.ArrayList<>();
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode parameterNode = field.getValue();
            List<String> enumValues = parameterNode.path("enum").isArray()
                    ? objectMapper.convertValue(parameterNode.path("enum"), new TypeReference<List<String>>() { })
                    : null;
            Object defaultValue = parameterNode.has("default")
                    ? objectMapper.convertValue(parameterNode.get("default"), Object.class)
                    : null;
            parameters.add(new ParameterSchema(
                    field.getKey(),
                    parameterNode.path("type").asText("string"),
                    parameterNode.path("description").asText("Parameter '" + field.getKey() + "'."),
                    requiredNames.contains(field.getKey()),
                    defaultValue,
                    enumValues
            ));
        }
        return parameters;
    }

    private JsonNode normalizeSchemaNode(Object inputSchema) {
        if (inputSchema == null) {
            return null;
        }
        JsonNode schemaNode = objectMapper.valueToTree(inputSchema);
        if (schemaNode.isTextual()) {
            try {
                return objectMapper.readTree(schemaNode.asText());
            } catch (Exception ignored) {
                return null;
            }
        }
        return schemaNode;
    }

    private String renderToolResult(CallToolResult result) throws Exception {
        String textContent = result.content() == null
                ? ""
                : result.content().stream()
                        .filter(TextContent.class::isInstance)
                        .map(TextContent.class::cast)
                        .map(TextContent::text)
                        .collect(Collectors.joining("\n"));

        if (!textContent.isBlank()) {
            return textContent;
        }
        if (result.structuredContent() != null) {
            return objectMapper.writeValueAsString(result.structuredContent());
        }
        return objectMapper.writeValueAsString(result);
    }

    private String messageFor(Exception ex) {
        return lastError != null && !lastError.isBlank() ? lastError : rootMessage(ex);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
