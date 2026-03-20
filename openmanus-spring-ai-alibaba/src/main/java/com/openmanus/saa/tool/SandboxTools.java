package com.openmanus.saa.tool;

import com.openmanus.saa.model.sandbox.SandboxCommandResponse;
import com.openmanus.saa.service.sandbox.SandboxService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SandboxTools {

    private final SandboxService sandboxService;

    public SandboxTools(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @Tool(description = "Execute a non-destructive shell command inside a Docker sandbox")
    public String runSandboxCommand(String command) {
        SandboxCommandResponse response = sandboxService.execute(command);
        return response.success() ? response.output() : "Sandbox command failed: " + response.output();
    }
}
