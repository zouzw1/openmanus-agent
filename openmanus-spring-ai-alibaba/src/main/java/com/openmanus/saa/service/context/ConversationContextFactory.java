package com.openmanus.saa.service.context;

import com.openmanus.saa.model.HumanFeedbackRequest;
import com.openmanus.saa.model.context.ConversationContext;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.WorkflowCheckpointService;
import com.openmanus.saa.service.session.SessionMemoryService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 创建 ConversationContext 的工厂类。
 */
@Component
public class ConversationContextFactory {

    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final SessionMemoryService sessionMemoryService;
    private final WorkflowCheckpointService checkpointService;

    public ConversationContextFactory(SessionMemoryService sessionMemoryService,
            WorkflowCheckpointService checkpointService) {
        this.sessionMemoryService = sessionMemoryService;
        this.checkpointService = checkpointService;
    }

    /**
     * 构建 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, DEFAULT_HISTORY_LIMIT);

        // 检查是否有中断的工作流
        String resolvedSessionId = session.sessionId();
        boolean hasInterrupted = checkpointService.isInterrupted(resolvedSessionId);
        Optional<HumanFeedbackRequest> pendingFeedback = hasInterrupted
                ? checkpointService.getPendingFeedback(resolvedSessionId)
                : Optional.empty();

        return ConversationContext.from(session, currentPrompt, history, hasInterrupted, pendingFeedback);
    }

    /**
     * 构建带自定义历史长度的 ConversationContext。
     */
    public ConversationContext create(String sessionId, String currentPrompt, int historyLimit) {
        Session session = sessionMemoryService.getOrCreate(sessionId);
        String history = sessionMemoryService.summarizeHistory(session, historyLimit);

        // 检查是否有中断的工作流
        String resolvedSessionId = session.sessionId();
        boolean hasInterrupted = checkpointService.isInterrupted(resolvedSessionId);
        Optional<HumanFeedbackRequest> pendingFeedback = hasInterrupted
                ? checkpointService.getPendingFeedback(resolvedSessionId)
                : Optional.empty();

        return ConversationContext.from(session, currentPrompt, history, hasInterrupted, pendingFeedback);
    }
}
