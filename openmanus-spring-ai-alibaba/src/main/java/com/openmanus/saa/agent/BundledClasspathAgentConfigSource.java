package com.openmanus.saa.agent;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Order(0)
public class BundledClasspathAgentConfigSource implements AgentConfigSource {

    private static final Logger log = LoggerFactory.getLogger(BundledClasspathAgentConfigSource.class);
    private static final List<String> BUNDLED_AGENT_RESOURCES = List.of(
            "openmanus/agents/manus.yml",
            "openmanus/agents/data-analysis.yml"
    );

    private final Yaml yaml = new Yaml();

    @Override
    @SuppressWarnings("unchecked")
    public List<AgentDefinition> loadAll() {
        List<AgentDefinition> definitions = new ArrayList<>();
        for (String resourcePath : BUNDLED_AGENT_RESOURCES) {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("Bundled agent resource not found on classpath: {}", resourcePath);
                continue;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                Object loaded = yaml.load(inputStream);
                if (!(loaded instanceof Map<?, ?> root)) {
                    throw new IllegalStateException("Bundled agent resource must contain a YAML object: " + resourcePath);
                }
                definitions.add(parseDefinition(resourcePath, (Map<String, Object>) root));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to load bundled agent definition from classpath resource " + resourcePath, ex);
            }
        }
        log.info("Loaded {} bundled agent definitions from classpath", definitions.size());
        return List.copyOf(definitions);
    }

    private AgentDefinition parseDefinition(String resourcePath, Map<String, Object> root) {
        String id = requiredString(root, "id", resourcePath);
        String name = optionalString(root, "name", id);
        boolean enabled = optionalBoolean(root, "enabled", true);
        String executorType = optionalString(root, "executor-type", optionalString(root, "executorType", id));
        String description = optionalString(root, "description", "No description provided.");
        boolean planningVisible = optionalBoolean(root, "planning-visible", optionalBoolean(root, "planningVisible", true));
        int priority = optionalInt(root, "priority", 100);
        String inlinePrompt = optionalString(root, "system-prompt", optionalString(root, "systemPrompt", ""));

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
                inlinePrompt == null ? "" : inlinePrompt.trim(),
                null,
                localTools,
                mcp,
                skills
        );
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

    private String requiredString(Map<String, Object> raw, String key, String resourcePath) {
        String value = optionalString(raw, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required field '" + key + "' in bundled agent resource: " + resourcePath);
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
