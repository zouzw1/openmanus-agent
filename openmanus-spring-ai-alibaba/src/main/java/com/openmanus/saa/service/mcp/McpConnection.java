package com.openmanus.saa.service.mcp;

import com.openmanus.saa.model.mcp.McpToolCallResult;
import java.util.List;

public interface McpConnection {

    String serverId();

    String type();

    String endpoint();

    boolean connected();

    List<String> listTools();

    McpToolCallResult invoke(String toolName, String argumentsJson);

    void close();
}
