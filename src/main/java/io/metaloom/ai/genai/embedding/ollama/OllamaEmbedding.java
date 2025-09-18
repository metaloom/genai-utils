package io.metaloom.ai.genai.embedding.ollama;

import java.util.Arrays;

import io.metaloom.ai.genai.Embedding;

public class OllamaEmbedding implements Embedding {

    private dev.langchain4j.data.embedding.Embedding embedding;

    public OllamaEmbedding(dev.langchain4j.data.embedding.Embedding e) {
        this.embedding = e;
    }

    @Override
    public float[] vector() {
        return embedding.vector();
    }

    @Override
    public String toString() {
        return Arrays.toString(embedding.vector());
    }

}
