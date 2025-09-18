package io.metaloom.ai.genai;

public interface EmbeddingModel {

    String url();

    String name();

    static EmbeddingModel fromOllama(String url, String modelName) {
        return new EmbeddingModel() {

            @Override
            public String url() {
                return url;
            }

            @Override
            public String name() {
                return modelName;
            }
        };
    }

}
