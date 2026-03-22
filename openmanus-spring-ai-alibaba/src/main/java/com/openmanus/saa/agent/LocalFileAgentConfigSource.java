package com.openmanus.saa.agent;

import com.openmanus.saa.config.AgentRegistryProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class LocalFileAgentConfigSource implements AgentConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFileAgentConfigSource.class);

    private final AgentRegistryProperties properties;
    private final Yaml yaml = new Yaml();

    public LocalFileAgentConfigSource(AgentRegistryProperties properties) {
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AgentDefinition> loadAll() {
        Path configDirectory = Path.of(properties.getConfigLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configDirectory);
            List<AgentDefinition> definitions = new ArrayList<>();
            try (var stream = Files.list(configDirectory)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String lower = path.getFileName().toString().toLowerCase();
                            return lower.endsWith(".yml") || lower.endsWith(".yaml");
                        })
                        .sorted()
                        .toList();
                for (Path file : files) {
                    try (InputStream inputStream = Files.newInputStream(file)) {
                        Object loaded = yaml.load(inputStream);
                        if (!(loaded instanceof Map<?, ?> root)) {
                            throw new IllegalStateException("Local agent file must contain a YAML object: " + file);
                        }
                        definitions.add(parseDefinition(file, (Map<String, Object>) root));
                    }
                }
            }

            if (definitions.isEmpty() && properties.isFailFast()) {
                throw new IllegalStateException("No local agent definitions found under " + configDirectory);
            }

            log.info("Loaded {} local agent definitions from {}", definitions.size(), configDirectory);
            return definitions;
        } catch (Exception ex) {
            if (properties.isFailFast()) {
                throw new IllegalStateException("Failed to load local agent definitions from " + configDirectory, ex);
            }
            log.warn("Failed to load local agent definitions from {}: {}", configDirectory, ex.getMessage(), ex);
            return List.of();
        }
    }

    private AgentDefinition parseDefinition(Path file, Map<String, Object> root) throws IOException {
        String id = requiredString(root, "id", file);
        String name = optionalString(root, "name", id);
        boolean enabled = optionalBoolean(root, "enabled", true);
        String executorType = optionalString(root, "executor-type", optionalString(root, "executorType", id));
        String description = optionalString(root, "description", "No description provided.");
        boolean planningVisible = optionalBoolean(root, "planning-visible", optionalBoolean(root, "planningVisible", true));
        int priority = optionalInt(root, "priority", 100);

        Map<String, Object> prompt = childMap(root, "prompt");
        String promptFile = optionalString(prompt, "file", null);
        String inlinePrompt = optionalString(root, "system-prompt", optionalString(root, "systemPrompt", ""));
        String resolvedPrompt = resolvePrompt(file, promptFile, inlinePrompt);

        Map<String, Object> capabilities = childMap(root, "capabilities");
        IdAccessPolicy localTools = parseIdAccessPolicy(childMap(capabilities, "local-tools"), CapabilityAccessMode.ALLOW_LIST, "tool:");
        IdAccessPolicy skills = parseIdAccessPolicy(childMap(capabilities, "skills"), CapabilityAccessMode.DENY_ALL, "skill:");
        McpAccessPolicy mcp = parseMcpAccessPolicy(childMap(capabilities, "mcp"));

        return new AgentDefinition(
                id,
                name,
                enabled,
                executorType,
                description,
                planningVisible,
                priority,
                resolvedPrompt,
                promptFile,
                localTools,
                mcp,
                skills
        );
    }

    private String resolvePrompt(Path file, String promptFile, String inlinePrompt) throws IOException {
        StringBuilder builder = new StringBuilder();
        if (inlinePrompt != null && !inlinePrompt.isBlank()) {
            builder.append(inlinePrompt.trim());
        }
        if (promptFile != null && !promptFile.isBlank()) {
            Path promptPath = file.getParent().resolve(promptFile).normalize();
            if (!Files.exists(promptPath)) {
                throw new IllegalStateException("Prompt file does not exist: " + promptPath);
            }
            String promptContent = Files.readString(promptPath, StandardCharsets.UTF_8).trim();
            if (builder.length() > 0 && !promptContent.isBlank()) {
                builder.append("\n\n");
            }
            builder.append(promptContent);
        }
        return builder.toString().trim();
    }

    private IdAccessPolicy parseIdAccessPolicy(Map<String, Object> raw, CapabilityAccessMode defaultMode, String prefix) {
        CapabilityAccessMode mode = CapabilityAccessMode.fromValue(optionalString(raw, "mode", null), defaultMode);
        Set<String> ids = normalizeIds(listOfStrings(raw.get("ids")), prefix);
        return new IdAccessPolicy(mode, ids);
    }

    private McpAccessPolicy parseMcpAccessPolicy(Map<String, Object> raw) {
        CapabilityAccessMode mode = CapabilityAccessMode.fromValue(optionalString(raw, "mode", null), CapabilityAccessMode.ALLOW_LIST);
        Set<String> servers = normalizeIds(listOfStrings(raw.get("servers")), null);
        Set<String> tools = normalizeIds(listOfStrings(raw.get("tools")), "mcp:");
        return new McpAccessPolicy(mode, servers, tools);
    }

    private Set<String> normalizeIds(List<String> ids, String prefix) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String value = id.trim();
            if (prefix != null && value.startsWith(prefix)) {
                value = value.substring(prefix.length());
            }
            normalized.add(value);
        }
        return normalized;
    }

    private Map<String, Object> childMap(Map<String, Object> parent, String key) {
        if (parent == null) {
            return Map.of();
        }
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Map.of();
    }

    private String requiredString(Map<String, Object> raw, String key, Path file) {
        String value = optionalString(raw, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required field '" + key + "' in local agent file: " + file);
        }
        return value.trim();
    }

    private String optionalString(Map<String, Object> raw, String key, String defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        Object value = raw.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private boolean optionalBoolean(Map<String, Object> raw, String key, boolean defaultValue) {
        String value = optionalString(raw, key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private int optionalInt(Map<String, Object> raw, String key, int defaultValue) {
        String value = optionalString(raw, key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private List<String> listOfStrings(Object raw) {
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : collection) {
            values.add(String.valueOf(item));
        }
        return values;
    }
}
