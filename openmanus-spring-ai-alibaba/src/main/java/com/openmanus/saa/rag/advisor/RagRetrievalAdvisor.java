package com.openmanus.saa.rag.advisor;

import com.openmanus.saa.agent.AgentRagConfig;
import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.model.KnowledgeScope;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import com.openmanus.saa.rag.model.RetrievalResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

public class RagRetrievalAdvisor implements CallAdvisor {

    private final String agentId;
    private final AgentRagConfig ragConfig;
    private final RagRetrievalService ragRetrievalService;
    private final RagProperties ragProperties;

    public RagRetrievalAdvisor(
            String agentId,
            AgentRagConfig ragConfig,
            RagRetrievalService ragRetrievalService,
            RagProperties ragProperties
    ) {
        this.agentId = agentId;
        this.ragConfig = ragConfig;
        this.ragRetrievalService = ragRetrievalService;
        this.ragProperties = ragProperties;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (request == null || request.prompt() == null || !ragConfig.usesAdvisor() || ragConfig.getKnowledgeBaseIds().isEmpty()) {
            return chain.nextCall(request);
        }

        String query = extractQuery(request.prompt());
        if (query.isBlank()) {
            return chain.nextCall(request);
        }

        String sessionId = request.context() == null ? null : asString(request.context().get("sessionId"));
        RetrievalResult retrievalResult = ragRetrievalService.retrieve(new RetrievalRequest(
                query,
                new KnowledgeScope(null, agentId, sessionId, List.copyOf(ragConfig.getKnowledgeBaseIds()), Map.of()),
                ragConfig.getTopK() == null ? ragProperties.getDefaultTopK() : ragConfig.getTopK(),
                ragProperties.isDefaultHybrid(),
                Map.of()
        ));

        if (retrievalResult == null || retrievalResult.hits() == null || retrievalResult.hits().isEmpty()) {
            return chain.nextCall(request);
        }

        String ragContext = renderRagContext(retrievalResult.hits());
        if (ragContext.isBlank()) {
            return chain.nextCall(request);
        }

        Prompt augmentedPrompt = request.prompt().augmentSystemMessage("""
                RAG KNOWLEDGE BASE CONTEXT:
                Use the following retrieved knowledge base snippets as supporting context when they are relevant.
                Prefer retrieved facts over guesswork, and do not claim details not present in the retrieved context.

                %s
                """.formatted(ragContext.trim()));

        Map<String, Object> context = request.context() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.context());
        context.put("ragKnowledgeBaseIds", List.copyOf(ragConfig.getKnowledgeBaseIds()));
        context.put("ragContext", ragContext);

        return chain.nextCall(new ChatClientRequest(augmentedPrompt, Map.copyOf(context)));
    }

    @Override
    public String getName() {
        return "ragRetrievalAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 200;
    }

    private String extractQuery(Prompt prompt) {
        List<UserMessage> userMessages = prompt.getUserMessages();
        if (userMessages == null || userMessages.isEmpty()) {
            return "";
        }
        return userMessages.stream()
                .map(UserMessage::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String renderRagContext(List<RetrievalHit> hits) {
        int remaining = Math.max(500, ragProperties.getRetrieval().getMaxContextChars());
        List<String> sections = new ArrayList<>();
        int index = 1;
        for (RetrievalHit hit : hits) {
            String source = extractMetadataString(hit.metadata(), "relativePath",
                    extractMetadataString(hit.metadata(), "source", hit.documentId()));
            String title = extractMetadataString(hit.metadata(), "title",
                    extractMetadataString(hit.metadata(), "fileName", hit.documentId()));
            String section = """
                    [%d] %s
                    source: %s
                    score: %.4f
                    content:
                    %s
                    """.formatted(
                    index++,
                    title == null || title.isBlank() ? hit.documentId() : title,
                    source == null || source.isBlank() ? "-" : source,
                    hit.score(),
                    hit.text() == null ? "" : hit.text().trim()
            ).trim();
            if (section.length() > remaining) {
                if (sections.isEmpty()) {
                    sections.add(section.substring(0, Math.max(0, remaining)));
                }
                break;
            }
            sections.add(section);
            remaining -= section.length();
            if (remaining <= 0) {
                break;
            }
        }
        return String.join("\n\n", sections).trim();
    }

    private String extractMetadataString(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
