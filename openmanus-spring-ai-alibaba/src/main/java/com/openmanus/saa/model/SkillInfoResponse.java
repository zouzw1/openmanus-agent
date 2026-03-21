package com.openmanus.saa.model;

public record SkillInfoResponse(
        String name,
        String description,
        String skillPath,
        String source
) {
}
