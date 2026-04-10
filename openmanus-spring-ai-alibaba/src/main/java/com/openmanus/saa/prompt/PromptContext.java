package com.openmanus.saa.prompt;

import com.openmanus.saa.agent.AgentDefinition;
import java.util.Map;

/**
 * Prompt 渲染上下文。
 * 包含构建 Prompt 所需的所有上下文信息。
 */
public record PromptContext(
    /**
     * 当前 Agent 定义
     */
    AgentDefinition agent,

    /**
     * 会话 ID（可选）
     */
    String sessionId,

    /**
     * 运行时提示信息
     */
    Map<String, Object> runtimeHints
) {

    public PromptContext {
        runtimeHints = runtimeHints != null ? Map.copyOf(runtimeHints) : Map.of();
    }

    /**
     * 创建仅包含 Agent 的上下文
     */
    public static PromptContext of(AgentDefinition agent) {
        return new PromptContext(agent, null, Map.of());
    }

    /**
     * 创建包含 Agent 和会话 ID 的上下文
     */
    public static PromptContext of(AgentDefinition agent, String sessionId) {
        return new PromptContext(agent, sessionId, Map.of());
    }

    /**
     * 获取运行时提示
     */
    public Object getHint(String key) {
        return runtimeHints.get(key);
    }

    /**
     * 获取运行时提示（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getHint(String key, T defaultValue) {
        return runtimeHints.containsKey(key)
            ? (T) runtimeHints.get(key)
            : defaultValue;
    }
}
