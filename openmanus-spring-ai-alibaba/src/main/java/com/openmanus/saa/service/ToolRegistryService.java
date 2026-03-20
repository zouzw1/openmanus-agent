package com.openmanus.saa.service;

import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.ToolMetadata.ParameterSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工具注册表服务，管理所有可用工具的元数据
 */
@Service
public class ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);

    private final Map<String, ToolMetadata> toolRegistry = new HashMap<>();

    public ToolRegistryService() {
        // 初始化内置工具的元数据
        initializeBuiltInTools();
    }

    /**
     * 注册工具元数据
     */
    public void registerTool(ToolMetadata metadata) {
        toolRegistry.put(metadata.getName(), metadata);
        log.info("Registered tool: {} with {} parameters", 
                metadata.getName(), metadata.getParameters().size());
    }

    /**
     * 获取工具元数据
     */
    public Optional<ToolMetadata> getTool(String toolName) {
        return Optional.ofNullable(toolRegistry.get(toolName));
    }

    /**
     * 获取所有已注册的 tools
     */
    public Collection<ToolMetadata> getAllTools() {
        return toolRegistry.values();
    }

    /**
     * 生成所有工具的 JSON Schema 集合（用于 LLM Prompt）
     */
    public String generateAllToolsJsonSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        
        List<ToolMetadata> tools = new ArrayList<>(toolRegistry.values());
        for (int i = 0; i < tools.size(); i++) {
            ToolMetadata tool = tools.get(i);
            sb.append(tool.toJsonSchema());
            if (i < tools.size() - 1) {
                sb.append(",");
            }
        }
        
        sb.append("]");
        return sb.toString();
    }

    /**
     * 生成所有工具的 Prompt 指导说明
     */
    public String generateToolsPromptGuidance() {
        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE TOOLS AND THEIR PARAMETER SCHEMAS:\n\n");
        
        for (ToolMetadata tool : toolRegistry.values()) {
            sb.append("========================================\n");
            sb.append(tool.toPromptGuidance());
            sb.append("\n");
        }
        
        sb.append("\n========================================\n");
        sb.append("IMPORTANT: When using any tool, you MUST provide ALL required parameters.\n");
        sb.append("If a required parameter is missing, check the conversation history first.\n");
        sb.append("If not found in history, ask the user explicitly for that parameter.\n");
        sb.append("NEVER assume default values for required parameters!\n");
        
        return sb.toString();
    }

    /**
     * 验证参数是否符合 Schema
     */
    public boolean validateParameters(String toolName, Map<String, Object> parameters) {
        ToolMetadata tool = toolRegistry.get(toolName);
        if (tool == null) {
            log.warn("Unknown tool: {}", toolName);
            return false;
        }

        // 检查必需参数
        for (ParameterSchema param : tool.getParameters()) {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                log.warn("Missing required parameter '{}' for tool '{}'", 
                        param.getName(), toolName);
                return false;
            }
        }

        // TODO: 可以添加更详细的类型验证
        return true;
    }

    /**
     * 提取缺失的必需参数列表
     */
    public List<String> getMissingRequiredParameters(String toolName, Map<String, Object> parameters) {
        List<String> missing = new ArrayList<>();
        ToolMetadata tool = toolRegistry.get(toolName);
        
        if (tool != null) {
            for (ParameterSchema param : tool.getParameters()) {
                if (param.isRequired() && !parameters.containsKey(param.getName())) {
                    missing.add(param.getName());
                }
            }
        }
        
        return missing;
    }

    /**
     * 初始化工具元数据
     */
    private void initializeBuiltInTools() {
        // MCP Tool Bridge
        registerTool(new ToolMetadata(
            "callMcpTool",
            "Call a tool from a connected MCP server",
            List.of(
                new ParameterSchema("serverId", "string", "The ID of the MCP server to call", true, null, null),
                new ParameterSchema("toolName", "string", "The name of the tool on the MCP server", true, null, null),
                new ParameterSchema("argumentsJson", "string", "JSON string containing the arguments to pass to the tool", true, null, null)
            ),
            Map.of("description", "Result from the MCP tool execution")
        ));

        // Weather Tool (示例)
        registerTool(new ToolMetadata(
            "get_weather",
            "Get current weather information for a city",
            List.of(
                new ParameterSchema("city", "string", "Name of the city to get weather for", true, null, null),
                new ParameterSchema("units", "string", "Temperature units: 'celsius' or 'fahrenheit'", false, "celsius", 
                    List.of("celsius", "fahrenheit"))
            ),
            Map.of(
                "description", "Current weather conditions including temperature, humidity, and conditions",
                "format", "JSON object with weather data"
            )
        ));

        // Time Tool (示例)
        registerTool(new ToolMetadata(
            "current_time",
            "Get current time for a specific timezone",
            List.of(
                new ParameterSchema("zoneId", "string", "Timezone ID (e.g., 'Asia/Shanghai', 'UTC')", false, "Asia/Shanghai", null)
            ),
            Map.of("description", "Current date and time in the specified timezone")
        ));

        // File Read Tool (示例)
        registerTool(new ToolMetadata(
            "read_file",
            "Read contents of a file",
            List.of(
                new ParameterSchema("filePath", "string", "Path to the file to read", true, null, null),
                new ParameterSchema("encoding", "string", "File encoding", false, "UTF-8", null)
            ),
            Map.of("description", "Contents of the file as text")
        ));

        // Shell Command Tool (示例)
        registerTool(new ToolMetadata(
            "execute_shell",
            "Execute a shell command in sandbox environment",
            List.of(
                new ParameterSchema("command", "string", "Shell command to execute", true, null, null),
                new ParameterSchema("timeout", "number", "Timeout in seconds", false, 30, null),
                new ParameterSchema("workingDir", "string", "Working directory for command execution", false, null, null)
            ),
            Map.of(
                "description", "Command output including stdout and stderr",
                "format", "Object with 'stdout', 'stderr', and 'exitCode' fields"
            )
        ));
    }
}
