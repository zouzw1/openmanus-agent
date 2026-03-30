package com.openmanus.saa.rag.model;

import java.util.List;
import java.util.Map;

public record KnowledgeScope(
        String tenantId,
        String agentId,
        String sessionId,
        List<String> knowledgeBaseIds,
        Map<String, Object> filters
) {
    public static KnowledgeScope ofKnowledgeBase(String knowledgeBaseId) {
        return new KnowledgeScope(null, null, null, List.of(knowledgeBaseId), Map.of());
    }
}
