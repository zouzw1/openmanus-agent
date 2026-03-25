package com.openmanus.saa.agent;

import com.openmanus.saa.config.AgentRegistryProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

@Service
public class AgentRegistryService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryService.class);

    private final AgentRegistryProperties properties;
    private final List<AgentConfigSource> agentConfigSources;
    private final AtomicReference<Map<String, AgentDefinition>> definitionsRef = new AtomicReference<>(Map.of());

    public AgentRegistryService(AgentRegistryProperties properties, List<AgentConfigSource> agentConfigSources) {
        this.properties = properties;
        this.agentConfigSources = agentConfigSources == null ? List.of() : List.copyOf(agentConfigSources);
    }

    @Override
    public void afterSingletonsInstantiated() {
        reload();
    }

    public synchronized List<AgentDefinition> reload() {
        List<AgentDefinition> loadedDefinitions = loadAllDefinitions();
        Map<String, AgentDefinition> next = new LinkedHashMap<>();
        for (AgentDefinition definition : loadedDefinitions) {
            if (next.containsKey(definition.getId())) {
                log.info("Overriding previously loaded agent definition for id '{}' with a later source entry.", definition.getId());
            }
            next.put(definition.getId(), definition);
        }
        definitionsRef.set(Map.copyOf(next));
        return listAll();
    }

    private List<AgentDefinition> loadAllDefinitions() {
        if (agentConfigSources.isEmpty()) {
            return List.of();
        }
        List<AgentDefinition> aggregated = new ArrayList<>();
        for (AgentConfigSource source : agentConfigSources) {
            if (source == null) {
                continue;
            }
            List<AgentDefinition> loaded = source.loadAll();
            if (loaded == null || loaded.isEmpty()) {
                continue;
            }
            aggregated.addAll(new ArrayList<>(new LinkedHashSet<>(loaded)));
        }
        return List.copyOf(aggregated);
    }

    public List<AgentDefinition> listAll() {
        List<AgentDefinition> definitions = new ArrayList<>(definitionsRef.get().values());
        definitions.sort(Comparator.comparingInt(AgentDefinition::getPriority).reversed().thenComparing(AgentDefinition::getId));
        return definitions;
    }

    public List<AgentDefinition> listEnabled() {
        return listAll().stream()
                .filter(AgentDefinition::isEnabled)
                .toList();
    }

    public List<AgentDefinition> listPlanningVisible() {
        return listEnabled().stream()
                .filter(AgentDefinition::isPlanningVisible)
                .toList();
    }

    public Optional<AgentDefinition> get(String agentId) {
        return Optional.ofNullable(definitionsRef.get().get(agentId));
    }

    public Optional<AgentDefinition> getEnabled(String agentId) {
        return get(agentId).filter(AgentDefinition::isEnabled);
    }

    public AgentDefinition getDefaultChatAgent() {
        return getEnabled(properties.getDefaultChatAgentId())
                .orElseGet(() -> listEnabled().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No enabled local agents are available.")));
    }
}
