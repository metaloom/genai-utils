package io.metaloom.ai.genai.llm.prompt;

import java.util.Map;

public interface Prompt {

	/**
	 * Return the prompt text with applied parameters.
	 * 
	 * @return
	 */
	String input();

	/**
	 * Return the prompt key for the prompt.
	 * 
	 * @return
	 */
	PromptKey key();

	/**
	 * Set parameter into the prompt.
	 * 
	 * @param key
	 * @param value
	 */
	default void set(String key, String value) {
		parameters().put(key, value);
	}

	/**
	 * Get a parameter from the prompt.
	 * 
	 * @param key
	 * @return
	 */
	default String get(String key) {
		return parameters().get(key);
	}

	/**
	 * Get set parameters.
	 * 
	 * @return
	 */
	Map<String, String> parameters();

	/**
	 * Return the template for the prompt.
	 * 
	 * @return
	 */
	String template();

	/**
	 * Text which will be appended to the llmInput
	 * 
	 * @param text
	 */
	void setText(String text);

}
