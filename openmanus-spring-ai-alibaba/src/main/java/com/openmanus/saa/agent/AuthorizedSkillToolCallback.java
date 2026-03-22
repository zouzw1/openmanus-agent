package com.openmanus.saa.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.service.SkillsService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class AuthorizedSkillToolCallback implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ToolDefinition TOOL_DEFINITION = ToolDefinition.builder()
            .name("read_skill")
            .description("Read the content of an allowed skill by skillName.")
            .inputSchema("""
                    {
                      "type": "object",
                      "properties": {
                        "skillName": { "type": "string", "description": "Exact allowed skill name." }
                      },
                      "required": ["skillName"]
                    }
                    """)
            .build();

    private final SkillsService skillsService;
    private final IdAccessPolicy accessPolicy;

    public AuthorizedSkillToolCallback(SkillsService skillsService, IdAccessPolicy accessPolicy) {
        this.skillsService = skillsService;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return TOOL_DEFINITION;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        if (!skillsService.isEnabled()) {
            return "Skills are disabled.";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toolInput);
            String skillName = root.path("skillName").asText("");
            if (skillName.isBlank()) {
                return "read_skill requires skillName.";
            }
            if (!accessPolicy.allows(skillName)) {
                return "Skill access blocked by agent policy: " + skillName;
            }
            return skillsService.readSkill(skillName);
        } catch (Exception ex) {
            return "Failed to read skill: " + ex.getMessage();
        }
    }
}
