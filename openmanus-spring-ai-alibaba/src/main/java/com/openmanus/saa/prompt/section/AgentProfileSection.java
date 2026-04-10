package com.openmanus.saa.prompt.section;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import org.springframework.stereotype.Component;

/**
 * Agent Profile Section。
 *
 * <p>渲染 Agent 的基本信息（ID、名称、类型、描述）。
 */
@Component
public class AgentProfileSection implements PromptSection {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String title() {
        return "## Agent Profile";
    }

    @Override
    public String render(PromptContext context) {
        AgentDefinition agent = context.agent();
        StringBuilder sb = new StringBuilder();

        sb.append("- Agent ID: ").append(agent.getId()).append("\n");
        sb.append("- Agent name: ").append(agent.getName()).append("\n");
        sb.append("- Executor type: ").append(agent.getExecutorType()).append("\n");
        sb.append("- Description: ").append(agent.getDescription());

        return sb.toString();
    }
}
