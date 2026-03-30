package com.openmanus.saa.rag.model;

import java.util.Map;

public record KnowledgeDocument(
        String knowledgeBaseId,
        String documentId,
        String title,
        String source,
        String content,
        Map<String, Object> metadata
) {
}
