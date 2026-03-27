package io.metaloom.ai.genai.llm;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.metaloom.ai.genai.llm.vllm.VLLMLLMProvider;

public class VLLMTest {

	@Test
	public void testLLM() {
		LargeLanguageModel model = VLLMTestModel.mistral24bQ8("http://localhost:11436/v1");
		Prompt prompt = new PromptImpl("Write hello world in plain text");
		LLMContext ctx = LLMContext.ctx(prompt, model).enableThink();

		LLMProvider provider = new VLLMLLMProvider();
		String text = provider.generate(ctx);
		System.out.println(text);
	}
}
