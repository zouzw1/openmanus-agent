package com.openmanus.saa.service.mcp;

import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.model.mcp.McpToolMetadata;
import java.util.List;

public interface McpConnection {

    String serverId();

    String type();

    String endpoint();

    boolean connected();

    List<String> listTools();

    default List<McpToolMetadata> listToolMetadata() {
        return listTools().stream()
                .map(toolName -> new McpToolMetadata(serverId(), toolName, "", List.of()))
                .toList();
    }

    McpToolCallResult invoke(String toolName, String argumentsJson);

    void close();
}
