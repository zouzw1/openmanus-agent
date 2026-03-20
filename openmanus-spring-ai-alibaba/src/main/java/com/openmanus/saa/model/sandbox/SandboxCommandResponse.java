package com.openmanus.saa.model.sandbox;

public record SandboxCommandResponse(
        boolean enabled,
        boolean success,
        String output
) {
}
