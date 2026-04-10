package com.openmanus.saa.prompt.section;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import com.openmanus.saa.service.ToolRegistryService;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 工具描述 Section。
 *
 * <p>渲染 Agent 可用的本地工具列表及其参数说明。
 */
@Component
public class ToolsPromptSection implements PromptSection {

    private final ToolRegistryService toolRegistryService;

    public ToolsPromptSection(ToolRegistryService toolRegistryService) {
        this.toolRegistryService = toolRegistryService;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String title() {
        return "## Available Tools";
    }

    @Override
    public String render(PromptContext context) {
        AgentDefinition agent = context.agent();

        // 获取所有已知工具
        Set<String> knownToolNames = toolRegistryService.getEnabledTools().stream()
            .map(ToolMetadata::getName)
            .collect(Collectors.toSet());

        // 解析允许的工具
        IdAccessPolicy localTools = agent.getLocalTools();
        Set<String> allowedToolNames = localTools.resolveAllowed(knownToolNames);

        return toolRegistryService.generateToolsPromptGuidance(allowedToolNames);
    }
}
