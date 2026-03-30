package com.openmanus.saa.rag.api;

import com.openmanus.saa.rag.model.KnowledgeScope;
import java.util.Map;

public interface KnowledgeScopeResolver {

    KnowledgeScope resolve(String sessionId, String agentId, Map<String, Object> context);
}
