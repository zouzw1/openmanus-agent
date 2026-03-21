package com.openmanus.saa.service;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.openmanus.saa.model.SkillInfoResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SkillsService {

    private final ObjectProvider<SkillRegistry> skillRegistryProvider;

    public SkillsService(ObjectProvider<SkillRegistry> skillRegistryProvider) {
        this.skillRegistryProvider = skillRegistryProvider;
    }

    public boolean isEnabled() {
        return skillRegistryProvider.getIfAvailable() != null;
    }

    public List<SkillInfoResponse> listSkills() {
        SkillRegistry skillRegistry = requireSkillRegistry();
        return skillRegistry.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<SkillInfoResponse> getSkill(String skillName) {
        SkillRegistry skillRegistry = requireSkillRegistry();
        return skillRegistry.get(skillName).map(this::toResponse);
    }

    public String readSkill(String skillName) throws IOException {
        return requireSkillRegistry().readSkillContent(skillName);
    }

    public String getLoadInstructions() {
        return requireSkillRegistry().getSkillLoadInstructions();
    }

    public int reload() {
        SkillRegistry skillRegistry = requireSkillRegistry();
        skillRegistry.reload();
        return skillRegistry.size();
    }

    private SkillRegistry requireSkillRegistry() {
        SkillRegistry skillRegistry = skillRegistryProvider.getIfAvailable();
        if (skillRegistry == null) {
            throw new IllegalStateException("Skills are not enabled.");
        }
        return skillRegistry;
    }

    private SkillInfoResponse toResponse(SkillMetadata metadata) {
        return new SkillInfoResponse(
                metadata.getName(),
                metadata.getDescription(),
                metadata.getSkillPath(),
                metadata.getSource()
        );
    }
}
