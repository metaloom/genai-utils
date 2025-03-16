package io.metaloom.ai.genai.llm;

import java.util.List;

import io.metaloom.ai.genai.llm.impl.LLMContextImpl;
import io.metaloom.ai.genai.llm.prompt.Prompt;

public interface LLMContext {

	/**
	 * Seed to be provided when invoking the LLM.
	 * 
	 * @return
	 */
	Integer seed();

	/**
	 * Temperature to be used for the LLM call.
	 * 
	 * @return
	 */
	double temperature();

	/**
	 * Model to utilize.
	 * 
	 * @return
	 */
	LargeLanguageModel model();

	/**
	 * Limitation on the generation.
	 * 
	 * @return
	 */
	int tokenOutputLimit();

	List<? extends ChatMessage> chatHistory();

	public static LLMContext ctx(List<? extends ChatMessage> history, LargeLanguageModel model) {
		return new LLMContextImpl(history, model);
	}

	public static LLMContext ctx(Prompt prompt, LargeLanguageModel model) {
		return ctx(prompt, null, model);
	}

	public static LLMContext ctx(Prompt prompt, String text, LargeLanguageModel model) {
		if (text == null) {
			text = "";
		}

		ChatMessage userMsg = ChatMessage.user(prompt.input() + text);
		List<? extends ChatMessage> history = List.of(userMsg);
		return ctx(history, model);
	}

	void setTemperature(double temperature);
}
