package com.openmanus.saa.service.mcp;

import com.openmanus.saa.model.mcp.McpToolCallResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StdioMcpConnection implements McpConnection {

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
        String payload = """
                {"jsonrpc":"2.0","id":"list-tools","method":"tools/list","params":{}}
                """;
        McpToolCallResult result = runRequest(payload, "tools/list");
        return List.of(result.output());
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
