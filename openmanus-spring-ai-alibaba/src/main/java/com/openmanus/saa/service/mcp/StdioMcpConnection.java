package com.openmanus.saa.service.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.ToolMetadata.ParameterSchema;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StdioMcpConnection implements McpConnection {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverId;
    private final String endpoint;
    private final String command;
    private final List<String> args;

    public StdioMcpConnection(String serverId, String command, List<String> args) {
        this.serverId = serverId;
        this.command = command;
        this.args = args == null ? List.of() : List.copyOf(args);
        this.endpoint = command + (this.args.isEmpty() ? "" : " " + String.join(" ", this.args));
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public String type() {
        return "stdio";
    }

    @Override
    public String endpoint() {
        return endpoint;
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public List<String> listTools() {
        return listToolMetadata().stream()
                .map(McpToolMetadata::name)
                .toList();
    }

    @Override
    public List<McpToolMetadata> listToolMetadata() {
        String payload = """
                {"jsonrpc":"2.0","id":"list-tools","method":"tools/list","params":{}}
                """;
        McpToolCallResult result = runRequest(payload, "tools/list");
        return parseToolMetadata(result);
    }

    @Override
    public McpToolCallResult invoke(String toolName, String argumentsJson) {
        String safeArgs = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        String payload = """
                {"jsonrpc":"2.0","id":"tool-call","method":"tools/call","params":{"name":"%s","arguments":%s}}
                """.formatted(toolName, safeArgs);
        return runRequest(payload, toolName);
    }

    @Override
    public void close() {
    }

    private List<McpToolMetadata> parseToolMetadata(McpToolCallResult result) {
        if (!result.success()) {
            return List.of(new McpToolMetadata(serverId, "unavailable", result.output(), List.of()));
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(result.output());
            JsonNode toolsNode = root.path("result").path("tools");
            if (!toolsNode.isArray()) {
                return List.of();
            }
            List<McpToolMetadata> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                tools.add(new McpToolMetadata(
                        serverId,
                        toolNode.path("name").asText(),
                        toolNode.path("description").asText(),
                        extractParameterSchemas(toolNode.path("inputSchema"))
                ));
            }
            return tools;
        } catch (Exception ignored) {
            return List.of(new McpToolMetadata(serverId, "unavailable", result.output(), List.of()));
        }
    }

    private List<ParameterSchema> extractParameterSchemas(JsonNode schemaNode) {
        if (schemaNode == null || !schemaNode.isObject()) {
            return List.of();
        }
        JsonNode propertiesNode = schemaNode.path("properties");
        if (!propertiesNode.isObject()) {
            return List.of();
        }
        Set<String> requiredNames = OBJECT_MAPPER.convertValue(
                schemaNode.path("required"),
                new TypeReference<Set<String>>() { }
        );
        List<ParameterSchema> parameters = new ArrayList<>();
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode parameterNode = field.getValue();
            List<String> enumValues = parameterNode.path("enum").isArray()
                    ? OBJECT_MAPPER.convertValue(parameterNode.path("enum"), new TypeReference<List<String>>() { })
                    : null;
            Object defaultValue = parameterNode.has("default")
                    ? OBJECT_MAPPER.convertValue(parameterNode.get("default"), Object.class)
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

    private McpToolCallResult runRequest(String payload, String toolName) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        Process process = null;
        try {
            process = new ProcessBuilder(commandLine).redirectErrorStream(true).start();
            process.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();
            boolean finished = process.waitFor(Duration.ofSeconds(15).toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new McpToolCallResult(serverId, toolName, false, "stdio MCP request timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new McpToolCallResult(serverId, toolName, process.exitValue() == 0, output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new McpToolCallResult(serverId, toolName, false, ex.getMessage());
        } catch (IOException ex) {
            return new McpToolCallResult(serverId, toolName, false, ex.getMessage());
        }
    }
}
