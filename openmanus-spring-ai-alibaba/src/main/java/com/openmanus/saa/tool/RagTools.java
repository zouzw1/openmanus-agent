package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.rag.api.RagIngestionService;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.model.IngestRequest;
import com.openmanus.saa.rag.model.IngestResult;
import com.openmanus.saa.rag.model.KnowledgeDocument;
import com.openmanus.saa.rag.model.KnowledgeScope;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import com.openmanus.saa.rag.model.RetrievalResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RagTools {

    private final ObjectProvider<RagIngestionService> ragIngestionServiceProvider;
    private final ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider;
    private final Path workspaceRoot;

    public RagTools(
            ObjectProvider<RagIngestionService> ragIngestionServiceProvider,
            ObjectProvider<RagRetrievalService> ragRetrievalServiceProvider,
            OpenManusProperties properties
    ) throws IOException {
        this.ragIngestionServiceProvider = ragIngestionServiceProvider;
        this.ragRetrievalServiceProvider = ragRetrievalServiceProvider;
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    @Tool(
            name = "rag_ingest",
            description = "Ingest a UTF-8 text file from the workspace into the configured RAG knowledge base"
    )
    public String ragIngest(
            @ToolParam(description = "Knowledge base id to ingest into, for example java_docs or travel_faq", required = true)
            String knowledgeBaseId,
            @ToolParam(description = "Relative file path inside the workspace to ingest", required = true)
            String relativePath,
            @ToolParam(description = "Optional document id. Defaults to the workspace-relative path.", required = false)
            String documentId,
            @ToolParam(description = "Optional document title. Defaults to the file name.", required = false)
            String title
    ) throws IOException {
        RagIngestionService ingestionService = ragIngestionServiceProvider.getIfAvailable();
        if (ingestionService == null) {
            return "RAG ingestion is not available. Enable openmanus.rag and configure an EmbeddingProvider first.";
        }

        Path target = resolve(relativePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return "Workspace file does not exist: " + workspaceRoot.relativize(target);
        }

        String normalizedRelativePath = workspaceRoot.relativize(target).toString().replace('\\', '/');
        String content = Files.readString(target, StandardCharsets.UTF_8);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("relativePath", normalizedRelativePath);
        metadata.put("fileName", target.getFileName().toString());
        metadata.put("title", title == null || title.isBlank() ? target.getFileName().toString() : title.trim());
        metadata.put("ingestSource", "workspace");

        KnowledgeDocument document = new KnowledgeDocument(
                knowledgeBaseId,
                documentId == null || documentId.isBlank() ? normalizedRelativePath : documentId.trim(),
                title == null || title.isBlank() ? target.getFileName().toString() : title.trim(),
                normalizedRelativePath,
                content,
                metadata
        );

        IngestResult result = ingestionService.ingest(new IngestRequest(knowledgeBaseId, List.of(document), Map.of()));
        return """
                RAG ingestion completed.
                - knowledgeBaseId: %s
                - documentCount: %d
                - chunkCount: %d
                - documentIds: %s
                """.formatted(
                result.knowledgeBaseId(),
                result.documentCount(),
                result.chunkCount(),
                result.documentIds()
        ).trim();
    }

    @Tool(
            name = "rag_search",
            description = "Search the configured RAG knowledge base and return the most relevant chunks with source information"
    )
    public String ragSearch(
            @ToolParam(description = "Natural-language query to search in the knowledge base", required = true)
            String query,
            @ToolParam(description = "Knowledge base id to search in, for example java_docs or travel_faq", required = true)
            String knowledgeBaseId,
            @ToolParam(description = "Maximum number of chunks to return. Defaults to 5.", required = false)
            Integer topK
    ) {
        RagRetrievalService retrievalService = ragRetrievalServiceProvider.getIfAvailable();
        if (retrievalService == null) {
            return "RAG retrieval is not available. Enable openmanus.rag and configure an EmbeddingProvider first.";
        }

        RetrievalResult result = retrievalService.retrieve(new RetrievalRequest(
                query,
                KnowledgeScope.ofKnowledgeBase(knowledgeBaseId),
                topK == null || topK <= 0 ? 5 : topK,
                true,
                Map.of()
        ));

        if (result.hits() == null || result.hits().isEmpty()) {
            return "No RAG results found for knowledge base '%s'.".formatted(knowledgeBaseId);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("RAG search results for knowledge base '")
                .append(knowledgeBaseId)
                .append("':\n");
        int index = 1;
        for (RetrievalHit hit : result.hits()) {
            String source = extractMetadataString(hit.metadata(), "relativePath", extractMetadataString(hit.metadata(), "source", hit.documentId()));
            String title = extractMetadataString(hit.metadata(), "title", extractMetadataString(hit.metadata(), "fileName", hit.documentId()));
            sb.append(index++)
                    .append(". [score=")
                    .append(String.format("%.4f", hit.score()))
                    .append("] ")
                    .append(title == null ? hit.documentId() : title)
                    .append("\n")
                    .append("   source: ")
                    .append(source == null ? "-" : source)
                    .append("\n")
                    .append("   chunkId: ")
                    .append(hit.chunkId())
                    .append("\n")
                    .append("   text: ")
                    .append(hit.text())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String extractMetadataString(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private Path resolve(String relativePath) {
        String safePath = normalizeWorkspaceRelativePath(relativePath);
        Path target = workspaceRoot.resolve(safePath == null || safePath.isBlank() ? "." : safePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        return target;
    }

    private String normalizeWorkspaceRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ".";
        }
        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.equals("workspace")) {
            return ".";
        }
        while (normalized.startsWith("workspace/")) {
            normalized = normalized.substring("workspace/".length());
        }
        return normalized;
    }
}
