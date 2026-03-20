package com.openmanus.saa.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.McpProperties;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.service.mcp.McpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final McpProperties properties;
    private final McpService mcpService;

    public McpToolBridge(McpProperties properties, McpService mcpService) {
        this.properties = properties;
        this.mcpService = mcpService;
    }

    @Tool(description = "Call a tool from a connected MCP server using serverId, toolName, and JSON arguments")
    public String callMcpTool(String serverId, String toolName, String argumentsJson) {
        if (!properties.isEnabled()) {
            return "MCP is disabled.";
        }
        
        // 记录工具调用输入
        log.info("=== MCP Tool Call Request ===");
        log.info("Server ID: {}", serverId);
        log.info("Tool Name: {}", toolName);
        log.info("Arguments JSON: {}", argumentsJson);
        
        McpToolCallResult result = mcpService.invoke(serverId, toolName, argumentsJson);
        
        // 记录原始返回结果
        log.info("=== MCP Tool Call Response ===");
        log.info("Server ID: {}", serverId);
        log.info("Tool Name: {}", toolName);
        log.info("Success: {}", result.success());
        log.info("Output (raw): {}", result.output());
        
        // 尝试格式化 JSON 输出以便更好查看
        try {
            Object formattedOutput = objectMapper.readValue(result.output(), Object.class);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(formattedOutput);
            log.info("Output (formatted JSON):\n{}", prettyJson);
        } catch (Exception e) {
            // 如果不是 JSON，直接输出
            log.info("Output (non-JSON): {}", result.output());
        }
        
        log.info("=============================");
        
        return result.success() ? result.output() : "MCP call failed: " + result.output();
    }
}
