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

	/**
	 * Turn reasoning on without stating an effort. Shorthand for
	 * {@code reasoning(ReasoningLevel.ON)}.
	 *
	 * @return fluent API
	 */
	LLMContext enableThink();

	/**
	 * Whether the context asks the model to reason.
	 *
	 * @return true for every {@link ReasoningLevel} but {@link ReasoningLevel#OFF} and
	 *         {@link ReasoningLevel#UNSET}
	 */
	boolean isThinkEnabled();

	/**
	 * Amount of reasoning ("thinking") the LLM should spend on the call.
	 *
	 * @return never null - {@link ReasoningLevel#UNSET} when no preference was configured
	 */
	ReasoningLevel reasoningLevel();

	/**
	 * Set the amount of reasoning the LLM should spend on the call. Not every model supports every
	 * level - see {@link ReasoningLevel}.
	 *
	 * @param level level to apply, null resets it to {@link ReasoningLevel#UNSET}
	 */
	void setReasoningLevel(ReasoningLevel level);

	/**
	 * Fluent variant of {@link #setReasoningLevel(ReasoningLevel)}.
	 *
	 * @param level
	 * @return fluent API
	 */
	default LLMContext reasoning(ReasoningLevel level) {
		setReasoningLevel(level);
		return this;
	}

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

	/**
	 * Whether streamed calls ask the server for token accounting ({@code stream_options.include_usage}).
	 * Servers that speak the OpenAI protocol - vLLM, llama.cpp, OpenAI - then append a final,
	 * choice-less chunk carrying the {@code usage} object of the call.
	 *
	 * @return true by default
	 */
	default boolean isStreamUsageEnabled() {
		return true;
	}
}
