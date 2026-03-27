package io.metaloom.ai.genai.llm;

import java.util.Collections;
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
	 * Set the seed value that will be passed on to the LLM.
	 * 
	 * @param seed
	 */
	void setSeed(Integer seed);

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

	public static LLMContext ctx(List<? extends ChatMessage> history, LargeLanguageModel model, Prompt prompt) {
		return new LLMContextImpl(history, model, prompt);
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
		return ctx(history, model, prompt);
	}

	void setTemperature(double temperature);

	LLMContext enableThink();

	boolean isThinkEnabled();

	Prompt prompt();

	/**
	 * Return the tool definitions available for tool calling.
	 *
	 * @return list of tool definitions, empty if none set
	 */
	default List<ToolDefinition> tools() {
		return Collections.emptyList();
	}

	/**
	 * Set the tool definitions to offer to the LLM.
	 *
	 * @param tools
	 */
	void setTools(List<ToolDefinition> tools);
}
