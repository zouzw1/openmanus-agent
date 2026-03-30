package com.openmanus.saa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.openmanus.saa.rag.api.ChunkingStrategy;
import com.openmanus.saa.rag.api.EmbeddingProvider;
import com.openmanus.saa.rag.api.KnowledgeScopeResolver;
import com.openmanus.saa.rag.api.RagBackendCapabilities;
import com.openmanus.saa.rag.api.RagDocumentStore;
import com.openmanus.saa.rag.api.RagIngestionService;
import com.openmanus.saa.rag.api.RagRetrievalService;
import com.openmanus.saa.rag.core.DefaultChunkingStrategy;
import com.openmanus.saa.rag.core.DefaultKnowledgeScopeResolver;
import com.openmanus.saa.rag.core.DefaultRagIngestionService;
import com.openmanus.saa.rag.core.DefaultRagRetrievalService;
import com.openmanus.saa.rag.elasticsearch.ElasticsearchRagBackendCapabilities;
import com.openmanus.saa.rag.elasticsearch.ElasticsearchRagDocumentStore;
import com.openmanus.saa.rag.springai.SpringAiEmbeddingProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "openmanus.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    @Bean
    @ConditionalOnMissingBean
    ChunkingStrategy chunkingStrategy(RagProperties properties) {
        return new DefaultChunkingStrategy(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    KnowledgeScopeResolver knowledgeScopeResolver() {
        return new DefaultKnowledgeScopeResolver();
    }

    @Bean
    @ConditionalOnProperty(prefix = "openmanus.rag", name = "backend", havingValue = "elasticsearch", matchIfMissing = true)
    @ConditionalOnMissingBean
    RagBackendCapabilities ragBackendCapabilities() {
        return new ElasticsearchRagBackendCapabilities();
    }

    @Bean
    @ConditionalOnProperty(prefix = "openmanus.rag", name = "backend", havingValue = "elasticsearch", matchIfMissing = true)
    @ConditionalOnMissingBean
    RagDocumentStore ragDocumentStore(RagProperties properties, ObjectMapper objectMapper) {
        return new ElasticsearchRagDocumentStore(properties, objectMapper);
    }

    @Bean
    @ConditionalOnClass(EmbeddingModel.class)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean
    EmbeddingProvider embeddingProvider(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingProvider(embeddingModel);
    }

    @Bean
    @ConditionalOnClass({DashScopeApi.class, DashScopeEmbeddingModel.class})
    @ConditionalOnBean(DashScopeApi.class)
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider dashScopeEmbeddingProvider(DashScopeApi dashScopeApi) {
        return new SpringAiEmbeddingProvider(new DashScopeEmbeddingModel(dashScopeApi));
    }

    @Bean
    @ConditionalOnBean({RagDocumentStore.class, EmbeddingProvider.class, ChunkingStrategy.class})
    @ConditionalOnMissingBean
    RagIngestionService ragIngestionService(
            RagDocumentStore documentStore,
            EmbeddingProvider embeddingProvider,
            ChunkingStrategy chunkingStrategy
    ) {
        return new DefaultRagIngestionService(documentStore, embeddingProvider, chunkingStrategy);
    }

    @Bean
    @ConditionalOnBean({RagDocumentStore.class, EmbeddingProvider.class})
    @ConditionalOnMissingBean
    RagRetrievalService ragRetrievalService(
            RagDocumentStore documentStore,
            EmbeddingProvider embeddingProvider
    ) {
        return new DefaultRagRetrievalService(documentStore, embeddingProvider);
    }
}
