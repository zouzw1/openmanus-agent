package com.openmanus.saa.service.context;

import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.springframework.stereotype.Component;

/**
 * 创建 ConversationContext 的工厂类。
 */
@Component
public class ConversationContextFactory {

    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final SessionMemoryService sessionMemoryService;

    public ConversationContextFactory(SessionMemoryService sessionMemoryService) {
        this.sessionMemoryService = sessionMemoryService;
    }

    /**
     * 构建 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, DEFAULT_HISTORY_LIMIT);
        return ConversationContext.from(session, currentPrompt, history);
    }

    /**
     * 构建带自定义历史长度的 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt, int historyLimit) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, historyLimit);
        return ConversationContext.from(session, currentPrompt, history);
    }
}
