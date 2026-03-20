package com.openmanus.saa.controller;

import com.openmanus.saa.model.mcp.McpConnectRequest;
import com.openmanus.saa.model.mcp.McpInvokeRequest;
import com.openmanus.saa.model.mcp.McpServerStatus;
import com.openmanus.saa.model.mcp.McpToolCallResult;
import com.openmanus.saa.service.mcp.McpService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping("/servers")
    public List<McpServerStatus> listServers() {
        return mcpService.listServers();
    }

    @PostMapping("/servers/connect")
    public McpServerStatus connect(@Valid @RequestBody McpConnectRequest request) {
        return mcpService.connect(request);
    }

    @PostMapping("/tools/call")
    public McpToolCallResult callTool(@Valid @RequestBody McpInvokeRequest request) {
        return mcpService.invoke(request.serverId(), request.toolName(), request.argumentsJson());
    }

    @DeleteMapping("/servers/{serverId}")
    public boolean disconnect(@PathVariable String serverId) {
        return mcpService.disconnect(serverId);
    }
}
