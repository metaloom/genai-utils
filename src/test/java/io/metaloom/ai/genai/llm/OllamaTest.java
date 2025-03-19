package io.metaloom.ai.genai.llm;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder;
import io.metaloom.ai.genai.llm.ollama.OllamaLLMProvider;
import io.metaloom.ai.genai.llm.omni.OmniProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.vertx.core.json.JsonObject;

public class OllamaTest {

	@Test
	public void testLLM() {
		LargeLanguageModel model = TestModel.OLLAMA_GEMMA3_27B_Q8;
		Prompt prompt = new PromptImpl("Write hello world");
		LLMContext ctx = LLMContext.ctx(prompt, model);

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
