package com.openmanus.saa.prompt.section;

import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import org.springframework.stereotype.Component;

/**
 * Agent 自定义 System Prompt Section。
 *
 * <p>渲染 AgentDefinition 中配置的 systemPrompt 字段。
 */
@Component
public class AgentSystemPromptSection implements PromptSection {

    @Override
    public int order() {
        return 10;  // 最先出现
    }

    @Override
    public String title() {
        return null;  // 无标题，直接追加内容
    }

    @Override
    public String render(PromptContext context) {
        String systemPrompt = context.agent().getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return "";
        }
        return systemPrompt.trim();
    }
}
