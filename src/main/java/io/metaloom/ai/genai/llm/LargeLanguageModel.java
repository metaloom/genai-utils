package io.metaloom.ai.genai.llm;

public interface LargeLanguageModel {

	/**
	 * Id being used to identify the model by the provider.
	 * 
	 * @return
	 */
	String id();

	/**
	 * URL to use to connect to when using the model.
	 * 
	 * @return
	 */
	String url();

	/**
	 * Return the context window size of the LLM.
	 * 
	 * @return
	 */
	int contextWindow();

	/**
	 * Vendor / Provider of the LLM service for the model.
	 * 
	 * @return
	 */
	LLMProviderType providerType();
}
