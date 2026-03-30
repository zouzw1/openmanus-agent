package com.openmanus.saa.rag.core;

import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.rag.api.ChunkingStrategy;
import com.openmanus.saa.rag.model.KnowledgeChunk;
import com.openmanus.saa.rag.model.KnowledgeDocument;
import java.util.ArrayList;
import java.util.List;

public class DefaultChunkingStrategy implements ChunkingStrategy {

    private final RagProperties properties;

    public DefaultChunkingStrategy(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        if (document == null || document.content() == null || document.content().isBlank()) {
            return List.of();
        }
        int chunkSize = Math.max(1, properties.getIngestion().getChunkSize());
        int overlap = Math.max(0, properties.getIngestion().getChunkOverlap());
        int step = Math.max(1, chunkSize - overlap);
        String content = document.content();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(content.length(), start + chunkSize);
            String text = content.substring(start, end).trim();
            if (!text.isBlank()) {
                chunks.add(new KnowledgeChunk(
                        document.knowledgeBaseId(),
                        document.documentId(),
                        document.documentId() + "#chunk-" + index++,
                        text,
                        document.metadata()
                ));
            }
            if (end >= content.length()) {
                break;
            }
        }
        return chunks;
    }
}
