package com.openmanus.saa.model.context;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一对话上下文对象，在入口处一次性构建，贯穿整个执行流程。
 */
public record ConversationContext(
    // 会话标识
    String sessionId,
    Session session,

    // 对话历史
    String conversationHistory,
    List<ConversationMessage> messages,

    // 用户偏好（从 workingMemory 提取）
    Map<String, Object> userPreferences,

    // 中断状态
    boolean hasInterruptedWorkflow,
    Optional<HumanFeedbackRequest> pendingFeedback,

    // 当前请求
    String currentPrompt
) {
    private static final String USER_PREFERENCES_KEY = "userPreferences";

    /**
     * 从 Session 构建 ConversationContext（无中断状态）。
     */
    public static ConversationContext from(Session session, String currentPrompt, String conversationHistory) {
        Map<String, Object> prefs = extractPreferences(session);
        return new ConversationContext(
            session.sessionId(),
            session,
            conversationHistory,
            session.messages(),
            prefs,
            false,
            Optional.empty(),
            currentPrompt
        );
    }

    /**
     * 从 Session 和中断状态构建 ConversationContext。
     */
    public static ConversationContext from(Session session, String currentPrompt, String conversationHistory,
            boolean hasInterruptedWorkflow, Optional<HumanFeedbackRequest> pendingFeedback) {
        Map<String, Object> prefs = extractPreferences(session);
        return new ConversationContext(
            session.sessionId(),
            session,
            conversationHistory,
            session.messages(),
            prefs,
            hasInterruptedWorkflow,
            pendingFeedback,
            currentPrompt
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPreferences(Session session) {
        return session.getMemory(USER_PREFERENCES_KEY, Map.class).orElse(Map.of());
    }

    /**
     * 是否有暂停的工作流需要恢复（兼容旧接口）。
     */
    public boolean hasPausedWorkflow() {
        return hasInterruptedWorkflow;
    }

    /**
     * 是否有用户偏好。
     */
    public boolean hasUserPreferences() {
        return userPreferences != null && !userPreferences.isEmpty();
    }

    /**
     * 获取偏好值的便捷方法。
     */
    @SuppressWarnings("unchecked")
    public <T> T getPreference(String key, Class<T> type, T defaultValue) {
        if (userPreferences == null) {
            return defaultValue;
        }
        Object value = userPreferences.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
}
