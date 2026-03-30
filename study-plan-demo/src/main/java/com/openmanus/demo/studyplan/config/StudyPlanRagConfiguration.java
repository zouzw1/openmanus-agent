package com.openmanus.demo.studyplan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.rag.api.ChunkingStrategy;
import com.openmanus.saa.rag.api.EmbeddingProvider;
import com.openmanus.saa.rag.api.RagDocumentStore;
import com.openmanus.saa.rag.api.RagIngestionService;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.core.DefaultChunkingStrategy;
import com.openmanus.saa.rag.core.DefaultRagIngestionService;
import com.openmanus.saa.rag.core.DefaultRagRetrievalService;
import com.openmanus.saa.rag.elasticsearch.ElasticsearchRagDocumentStore;
import com.openmanus.saa.rag.springai.SpringAiEmbeddingProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudyPlanRagConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider embeddingProvider(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingProvider(embeddingModel);
    }

    @Bean
    @ConditionalOnMissingBean(ChunkingStrategy.class)
    ChunkingStrategy chunkingStrategy(RagProperties properties) {
        return new DefaultChunkingStrategy(properties);
    }

    @Bean
    @ConditionalOnMissingBean(RagDocumentStore.class)
    RagDocumentStore ragDocumentStore(RagProperties properties, ObjectMapper objectMapper) {
        return new ElasticsearchRagDocumentStore(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RagIngestionService.class)
    RagIngestionService ragIngestionService(
            RagDocumentStore documentStore,
            EmbeddingProvider embeddingProvider,
            ChunkingStrategy chunkingStrategy
    ) {
        return new DefaultRagIngestionService(documentStore, embeddingProvider, chunkingStrategy);
    }

    @Bean
    @ConditionalOnMissingBean(RagRetrievalService.class)
    RagRetrievalService ragRetrievalService(
            RagDocumentStore documentStore,
            EmbeddingProvider embeddingProvider
    ) {
        return new DefaultRagRetrievalService(documentStore, embeddingProvider);
    }
}
