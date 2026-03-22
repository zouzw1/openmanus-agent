package com.openmanus.saa.model.mcp;

import com.openmanus.saa.model.ToolMetadata.ParameterSchema;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record McpToolMetadata(
        String serverId,
        String name,
        String description,
        List<ParameterSchema> parameters
) {

    public McpToolMetadata {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public String qualifiedName() {
        return serverId + "/" + name;
    }

    public Set<String> allParameterNames() {
        return parameters.stream()
                .map(ParameterSchema::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> requiredParameterNames() {
        return parameters.stream()
                .filter(ParameterSchema::isRequired)
                .map(ParameterSchema::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public String toPromptGuidance() {
        StringBuilder sb = new StringBuilder();
        sb.append("  - tool=").append(name).append(": ")
                .append(description == null || description.isBlank() ? "No description provided." : description)
                .append("\n");

        if (parameters.isEmpty()) {
            sb.append("    Parameters: none declared\n");
            return sb.toString();
        }

        sb.append("    Required Parameters:\n");
        boolean hasRequired = false;
        for (ParameterSchema parameter : parameters) {
            if (!parameter.isRequired()) {
                continue;
            }
            hasRequired = true;
            sb.append("      - ")
                    .append(parameter.getName())
                    .append(" (")
                    .append(parameter.getType())
                    .append("): ")
                    .append(parameter.getDescription())
                    .append("\n");
        }
        if (!hasRequired) {
            sb.append("      - none\n");
        }

        List<ParameterSchema> optionalParameters = parameters.stream()
                .filter(parameter -> !parameter.isRequired())
                .toList();
        if (!optionalParameters.isEmpty()) {
            sb.append("    Optional Parameters:\n");
            for (ParameterSchema parameter : optionalParameters) {
                sb.append("      - ")
                        .append(parameter.getName())
                        .append(" (")
                        .append(parameter.getType())
                        .append("): ")
                        .append(parameter.getDescription())
                        .append("\n");
            }
        }
        return sb.toString();
    }
}
