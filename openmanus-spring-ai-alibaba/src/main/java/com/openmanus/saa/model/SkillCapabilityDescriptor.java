package com.openmanus.saa.model;

import java.util.List;

public record SkillCapabilityDescriptor(
        String skillName,
        List<String> operations,
        List<String> inputFormats,
        List<String> outputFormats,
        List<String> executionHints,
        String planningHint
) {
    public SkillCapabilityDescriptor {
        skillName = skillName == null ? "" : skillName.trim();
        operations = operations == null ? List.of() : List.copyOf(operations);
        inputFormats = inputFormats == null ? List.of() : List.copyOf(inputFormats);
        outputFormats = outputFormats == null ? List.of() : List.copyOf(outputFormats);
        executionHints = executionHints == null ? List.of() : List.copyOf(executionHints);
        planningHint = planningHint == null ? "" : planningHint.trim();
    }
}
