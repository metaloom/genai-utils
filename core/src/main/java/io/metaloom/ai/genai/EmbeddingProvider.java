package io.metaloom.ai.genai;

import io.metaloom.ai.genai.embedding.ollama.OllamaEmbedding;

public interface EmbeddingProvider {

    OllamaEmbedding embed(EmbeddingModel model, String text);

}
