package com.openmanus.saa.rag.core;

import com.openmanus.saa.rag.api.KnowledgeScopeResolver;
import com.openmanus.saa.rag.model.KnowledgeScope;
import java.util.List;
import java.util.Map;

public class DefaultKnowledgeScopeResolver implements KnowledgeScopeResolver {

    @Override
    public KnowledgeScope resolve(String sessionId, String agentId, Map<String, Object> context) {
        Object rawKnowledgeBaseId = context == null ? null : context.get("knowledgeBaseId");
        List<String> knowledgeBaseIds = rawKnowledgeBaseId instanceof String knowledgeBaseId && !knowledgeBaseId.isBlank()
                ? List.of(knowledgeBaseId)
                : List.of();
        return new KnowledgeScope(
                null,
                agentId,
                sessionId,
                knowledgeBaseIds,
                context == null ? Map.of() : Map.copyOf(context)
        );
    }
}
