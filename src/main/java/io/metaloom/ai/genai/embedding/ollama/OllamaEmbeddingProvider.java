package io.metaloom.ai.genai.embedding.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder;
import io.metaloom.ai.genai.EmbeddingModel;
import io.metaloom.ai.genai.EmbeddingProvider;

public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

    @Override
    public OllamaEmbedding embed(EmbeddingModel model, String text) {
        String url = model.url();
        OllamaEmbeddingModelBuilder builder = OllamaEmbeddingModel.builder()
                .baseUrl(url)
                .modelName(model.name());

        OllamaEmbeddingModel embeddingModel = builder.build();

        dev.langchain4j.data.embedding.Embedding e = embeddingModel.embed(text).content();
        return new OllamaEmbedding(e);
    }
}
