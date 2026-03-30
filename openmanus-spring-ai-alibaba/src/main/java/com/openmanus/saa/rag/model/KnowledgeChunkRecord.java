package com.openmanus.saa.rag.model;

import java.util.List;
import java.util.Map;

public record KnowledgeChunkRecord(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String title,
        String titleKeyword,
        String source,
        String sourceType,
        String text,
        float[] vector,
        Integer chunkIndex,
        Integer chunkStart,
        Integer chunkEnd,
        Integer chunkLength,
        List<String> tags,
        String language,
        String category,
        String version,
        String author,
        String embeddingModel,
        Integer embeddingDimensions,
        String embeddingVersion,
        String createdAt,
        String updatedAt,
        String ingestedAt,
        String ingestBatchId,
        Boolean deleted,
        Map<String, Object> metadata
) {
}
