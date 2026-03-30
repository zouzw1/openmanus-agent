package com.openmanus.saa.rag.model;

import java.util.Map;

public record RetrievalHit(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String text,
        double score,
        Map<String, Object> metadata
) {
}
