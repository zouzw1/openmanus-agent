package com.openmanus.saa.service;

import com.openmanus.saa.model.SkillCapabilityDescriptor;
import com.openmanus.saa.model.SkillInfoResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SkillCapabilityService {

    private final SkillsService skillsService;
    private final Map<String, SkillCapabilityDescriptor> registry;

    public SkillCapabilityService(SkillsService skillsService) {
        this.skillsService = skillsService;
        this.registry = buildRegistry();
    }

    public Optional<SkillCapabilityDescriptor> getCapability(String skillName) {
        String normalized = normalizeSkillName(skillName);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(normalized));
    }

    public List<SkillCapabilityDescriptor> listAvailableCapabilities() {
        if (!skillsService.isEnabled()) {
            return List.of();
        }
        return skillsService.listSkills().stream()
                .map(SkillInfoResponse::name)
                .map(this::normalizeSkillName)
                .filter(Objects::nonNull)
                .map(registry::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public Optional<String> findMentionedSkillName(String text) {
        if (text == null || text.isBlank() || !skillsService.isEnabled()) {
            return Optional.empty();
        }
        String normalizedText = text.toLowerCase();
        return skillsService.listSkills().stream()
                .map(SkillInfoResponse::name)
                .map(this::normalizeSkillName)
                .filter(Objects::nonNull)
                .filter(skillName -> normalizedText.contains(skillName.toLowerCase()))
                .findFirst();
    }

    public Optional<String> resolveUniqueSkillForOutputFormat(String outputFormat) {
        if (outputFormat == null || outputFormat.isBlank()) {
            return Optional.empty();
        }
        List<SkillCapabilityDescriptor> matches = listAvailableCapabilities().stream()
                .filter(capability -> capability.outputFormats().contains(outputFormat))
                .toList();
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0).skillName());
    }

    private Map<String, SkillCapabilityDescriptor> buildRegistry() {
        Map<String, SkillCapabilityDescriptor> descriptors = new LinkedHashMap<>();
        register(descriptors, new SkillCapabilityDescriptor(
                "project-planning",
                List.of("plan", "draft"),
                List.of("text"),
                List.of("md", "txt"),
                List.of("createPlan", "writeWorkspaceFile"),
                "Use for creating structured plan drafts. It produces text drafts, not final office documents."
        ));
        register(descriptors, new SkillCapabilityDescriptor(
                "pdf",
                List.of("export", "render", "format"),
                List.of("md", "txt", "html"),
                List.of("pdf"),
                List.of("runPowerShell", "listWorkspaceFiles", "readWorkspaceFile", "writeWorkspaceFile"),
                "Use only for final PDF export/formatting steps."
        ));
        register(descriptors, new SkillCapabilityDescriptor(
                "docx",
                List.of("export", "render", "format"),
                List.of("md", "txt", "html"),
                List.of("docx"),
                List.of("runPowerShell", "listWorkspaceFiles", "readWorkspaceFile", "writeWorkspaceFile"),
                "Use only for final Word document export steps."
        ));
        register(descriptors, new SkillCapabilityDescriptor(
                "pptx",
                List.of("export", "render", "format"),
                List.of("md", "txt", "html"),
                List.of("pptx"),
                List.of("runPowerShell", "listWorkspaceFiles", "readWorkspaceFile", "writeWorkspaceFile"),
                "Use only for final slide export steps."
        ));
        register(descriptors, new SkillCapabilityDescriptor(
                "markdown-converter",
                List.of("convert"),
                List.of("pdf", "docx", "pptx", "html"),
                List.of("md"),
                List.of("readWorkspaceFile", "writeWorkspaceFile", "runPowerShell"),
                "This skill converts supported source files into Markdown. It is not a Markdown-to-PDF exporter."
        ));
        register(descriptors, new SkillCapabilityDescriptor(
                "tavily-search-pro",
                List.of("search", "research"),
                List.of("text"),
                List.of("text"),
                List.of(),
                "This is a local skill loaded via read_skill. Treat it as workflow guidance, not as a local tool or MCP tool name."
        ));
        return Map.copyOf(descriptors);
    }

    private void register(Map<String, SkillCapabilityDescriptor> descriptors, SkillCapabilityDescriptor descriptor) {
        descriptors.put(normalizeSkillName(descriptor.skillName()), descriptor);
    }

    private String normalizeSkillName(String skillName) {
        if (skillName == null) {
            return null;
        }
        String normalized = skillName.trim();
        if (normalized.startsWith("skill:")) {
            normalized = normalized.substring("skill:".length()).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }
}
