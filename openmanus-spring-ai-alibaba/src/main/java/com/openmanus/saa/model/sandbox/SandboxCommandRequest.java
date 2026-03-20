package com.openmanus.saa.model.sandbox;

import jakarta.validation.constraints.NotBlank;

public record SandboxCommandRequest(
        @NotBlank String command
) {
}
