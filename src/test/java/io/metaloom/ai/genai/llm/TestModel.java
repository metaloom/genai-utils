package io.metaloom.ai.genai.llm;

public enum TestModel implements LargeLanguageModel {

	OLLAMA_GEMMA2_27B("gemma2:27b", LLMProviderType.OLLAMA),

	OLLAMA_GEMMA3_27B_Q8("gemma3:27b-it-q8_0", LLMProviderType.OLLAMA),

	OLLAMA_GEMMA3_12B_Q8("gemma3:12b-it-q8_0", LLMProviderType.OLLAMA);

	private String id;

	private LLMProviderType providerType;

	TestModel(String id, LLMProviderType type) {
		this.id = id;
		this.providerType = type;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String url() {
		return TestEnv.OLLAMA_URL;
	}

	@Override
	public int contextWindow() {
		return 4096;
	}

	@Override
	public LLMProviderType providerType() {
		return providerType;
	}

}
