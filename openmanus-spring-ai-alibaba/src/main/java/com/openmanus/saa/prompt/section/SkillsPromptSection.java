package com.openmanus.saa.prompt.section;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.model.SkillInfoResponse;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import com.openmanus.saa.service.SkillsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 技能状态 Section。
 *
 * <p>渲染 Agent 可用的技能列表。
 */
@Component
public class SkillsPromptSection implements PromptSection {

    private final SkillsService skillsService;

    public SkillsPromptSection(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public String title() {
        return "## Skills Status";
    }

    @Override
    public String render(PromptContext context) {
        AgentDefinition agent = context.agent();
        IdAccessPolicy skillsConfig = agent.getSkills();

        if (skillsConfig.isDenied()) {
            return "Skills are disabled for this agent.";
        }

        // 获取所有可用技能
        Set<String> availableSkillNames = skillsService.listSkills().stream()
            .map(SkillInfoResponse::name)
            .collect(Collectors.toSet());

        // 解析允许的技能
        Set<String> allowedSkills = skillsConfig.resolveAllowed(availableSkillNames);

        if (allowedSkills.isEmpty()) {
            return "No skills are enabled for this agent.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- Allowed skills are accessible only via the read_skill tool.\n");

        List<SkillInfoResponse> allowedSkillInfos = skillsService.listSkills().stream()
            .filter(skill -> allowedSkills.contains(skill.name()))
            .toList();

        for (SkillInfoResponse skill : allowedSkillInfos) {
            sb.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n");
        }

        return sb.toString().trim();
    }
}
