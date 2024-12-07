package io.metaloom.ai.genai.llm;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.ollama.OllamaLLMProvider;
import io.metaloom.ai.genai.llm.omni.OmniProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;

public class OllamaTest {

	@Test
	public void testLLM() {
		LargeLanguageModel model = TestModel.OLLAMA_GEMMA2_27B;
		Prompt prompt = new PromptImpl("Write hello world");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		LLMProvider provider = new OllamaLLMProvider();
		String text = provider.generate(ctx);
		System.out.println(text);
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
