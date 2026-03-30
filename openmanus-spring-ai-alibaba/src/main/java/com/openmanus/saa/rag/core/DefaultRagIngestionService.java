package com.openmanus.saa.rag.core;

import com.openmanus.saa.rag.api.ChunkingStrategy;
import com.openmanus.saa.rag.api.EmbeddingProvider;
import com.openmanus.saa.rag.api.RagDocumentStore;
import com.openmanus.saa.rag.api.RagIngestionService;
import com.openmanus.saa.rag.model.IngestRequest;
import com.openmanus.saa.rag.model.IngestResult;
import com.openmanus.saa.rag.model.KnowledgeChunk;
import com.openmanus.saa.rag.model.KnowledgeChunkRecord;
import com.openmanus.saa.rag.model.KnowledgeDocument;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultRagIngestionService implements RagIngestionService {

    private final RagDocumentStore documentStore;
    private final EmbeddingProvider embeddingProvider;
    private final ChunkingStrategy chunkingStrategy;

    public DefaultRagIngestionService(
            RagDocumentStore documentStore,
            EmbeddingProvider embeddingProvider,
            ChunkingStrategy chunkingStrategy
    ) {
        this.documentStore = documentStore;
        this.embeddingProvider = embeddingProvider;
        this.chunkingStrategy = chunkingStrategy;
    }

    @Override
    public IngestResult ingest(IngestRequest request) {
        List<KnowledgeDocument> documents = request == null || request.documents() == null ? List.of() : request.documents();
        List<KnowledgeChunkRecord> records = new ArrayList<>();
        String ingestBatchId = UUID.randomUUID().toString();
        String ingestedAt = Instant.now().toString();
        for (KnowledgeDocument document : documents) {
            List<KnowledgeChunk> chunks = chunkingStrategy.chunk(document);
            List<String> texts = chunks.stream().map(KnowledgeChunk::text).toList();
            List<float[]> vectors = texts.isEmpty() ? List.of() : embeddingProvider.embedAll(texts);
            Map<String, Object> documentMetadata = document.metadata() == null ? Map.of() : document.metadata();
            int searchCursor = 0;
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                float[] vector = i < vectors.size() ? vectors.get(i) : embeddingProvider.embed(chunk.text());
                int[] chunkRange = locateChunkRange(document.content(), chunk.text(), searchCursor);
                searchCursor = Math.max(searchCursor, chunkRange[0] + 1);
                records.add(new KnowledgeChunkRecord(
                        metadataString(documentMetadata, "tenantId", "default"),
                        chunk.knowledgeBaseId(),
                        chunk.documentId(),
                        chunk.chunkId(),
                        document.title(),
                        blankToNull(document.title()),
                        document.source(),
                        inferSourceType(document.source()),
                        chunk.text(),
                        vector,
                        i,
                        chunkRange[0],
                        chunkRange[1],
                        chunk.text() == null ? 0 : chunk.text().length(),
                        metadataStringList(documentMetadata, "tags"),
                        metadataString(documentMetadata, "language", "unknown"),
                        metadataString(documentMetadata, "category", null),
                        metadataString(documentMetadata, "version", null),
                        metadataString(documentMetadata, "author", null),
                        metadataString(documentMetadata, "embeddingModel", "spring-ai"),
                        vector == null ? 0 : vector.length,
                        metadataString(documentMetadata, "embeddingVersion", "v1"),
                        metadataString(documentMetadata, "createdAt", ingestedAt),
                        metadataString(documentMetadata, "updatedAt", ingestedAt),
                        ingestedAt,
                        ingestBatchId,
                        false,
                        chunk.metadata()
                ));
            }
        }
        if (!records.isEmpty()) {
            documentStore.upsertChunks(records);
        }
        return new IngestResult(
                request == null ? null : request.knowledgeBaseId(),
                documents.size(),
                records.size(),
                documents.stream().map(KnowledgeDocument::documentId).toList()
        );
    }

    private int[] locateChunkRange(String content, String chunkText, int searchStart) {
        if (content == null || content.isBlank() || chunkText == null || chunkText.isBlank()) {
            return new int[] {-1, -1};
        }
        int normalizedSearchStart = Math.max(0, Math.min(searchStart, Math.max(0, content.length() - 1)));
        int start = content.indexOf(chunkText, normalizedSearchStart);
        if (start < 0) {
            start = content.indexOf(chunkText);
        }
        if (start < 0) {
            start = normalizedSearchStart;
        }
        int end = Math.min(content.length(), start + chunkText.length());
        return new int[] {start, end};
    }

    private String inferSourceType(String source) {
        if (source == null || source.isBlank()) {
            return "manual";
        }
        String normalized = source.trim().toLowerCase();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return "url";
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains(".")) {
            return "workspace_file";
        }
        return "manual";
    }

    private String metadataString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null || metadata.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(metadata.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private List<String> metadataStringList(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return List.of();
        }
        Object value = metadata.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? List.of() : List.of(stringValue);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
