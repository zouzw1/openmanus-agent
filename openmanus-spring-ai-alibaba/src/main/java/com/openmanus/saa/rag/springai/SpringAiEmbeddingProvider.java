package com.openmanus.saa.rag.springai;

import com.openmanus.saa.rag.api.EmbeddingProvider;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return embeddingModel.embed(texts == null ? List.of() : texts);
    }
}
