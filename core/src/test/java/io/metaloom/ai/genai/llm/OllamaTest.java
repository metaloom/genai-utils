package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.EmbeddingModel;
import io.metaloom.ai.genai.EmbeddingProvider;
import io.metaloom.ai.genai.embedding.ollama.OllamaEmbedding;
import io.metaloom.ai.genai.embedding.ollama.OllamaEmbeddingProvider;
import io.metaloom.ai.genai.llm.ollama.OllamaLLMProvider;
import io.metaloom.ai.genai.llm.omni.OmniProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.vertx.core.json.JsonObject;

public class OllamaTest {

    @Test
    public void testLLM() {
        LargeLanguageModel model = TestModel.OLLAMA_MAGISTRAL_24B;
        Prompt prompt = new PromptImpl("Write hello world in plain text");
        LLMContext ctx = LLMContext.ctx(prompt, model).enableThink();

        LLMProvider provider = new OllamaLLMProvider();
        String text = provider.generate(ctx);
        System.out.println(text);
    }

    @Test
    public void testJSON() {
        LargeLanguageModel model = TestModel.OLLAMA_GEMMA2_27B;
        Prompt prompt = new PromptImpl("Write hello world in JSON");
        LLMContext ctx = LLMContext.ctx(prompt, model);

        LLMProvider provider = new OllamaLLMProvider();
        JsonObject json = provider.generateJson(ctx);
        System.out.println(json.encodePrettily());
    }

    @Test
    public void testEmbedding() {
        String modelName = "embeddinggemma:300m";
        EmbeddingProvider provider = new OllamaEmbeddingProvider();
        EmbeddingModel model = EmbeddingModel.fromOllama(TestEnv.OLLAMA_URL, modelName);
        OllamaEmbedding result = provider.embed(model, "Hello World");
        assertNotNull(result);
        System.out.println(result);
    }

    @Test
    public void testOmni() {
        LargeLanguageModel model = TestModel.OLLAMA_GEMMA2_27B;
        Prompt prompt = new PromptImpl("Write hello world");
        LLMContext ctx = LLMContext.ctx(prompt, model);

        LLMProvider provider = new OllamaLLMProvider();
        LLMProvider omni = new OmniProvider(provider);
        String text = omni.generate(ctx);
        System.out.println(text);
    }
}
