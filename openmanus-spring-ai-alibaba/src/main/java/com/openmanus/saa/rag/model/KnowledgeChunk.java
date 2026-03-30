package com.openmanus.saa.rag.model;

import java.util.Map;

public record KnowledgeChunk(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String text,
        Map<String, Object> metadata
) {
}
