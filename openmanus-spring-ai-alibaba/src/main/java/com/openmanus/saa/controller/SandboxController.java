package com.openmanus.saa.controller;

import com.openmanus.saa.model.sandbox.SandboxCommandRequest;
import com.openmanus.saa.model.sandbox.SandboxCommandResponse;
import com.openmanus.saa.service.sandbox.SandboxService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostMapping("/execute")
    public SandboxCommandResponse execute(@Valid @RequestBody SandboxCommandRequest request) {
        return sandboxService.execute(request.command());
    }
}
