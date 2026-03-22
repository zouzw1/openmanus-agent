package com.openmanus.saa.agent;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallback;

public record ResolvedAgentRuntime(
        AgentDefinition agentDefinition,
        String systemPrompt,
        List<ToolCallback> toolCallbacks,
        List<Advisor> advisors,
        Map<String, Object> toolContext
) {
}
