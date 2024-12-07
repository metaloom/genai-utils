package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.LLMProviderType;
import io.metaloom.ai.genai.llm.LargeLanguageModel;

public class LargeLanguageModelImpl implements LargeLanguageModel {

	private String id;
	private String url;
	private int contextWindow;
	private LLMProviderType type;

	public LargeLanguageModelImpl(String id, String url, int contextWindow, LLMProviderType type) {
		this.id = id;
		this.url = url;
		this.contextWindow = contextWindow;
		this.type = type;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String url() {
		return url;
	}

	@Override
	public int contextWindow() {
		return contextWindow;
	}

	@Override
	public LLMProviderType providerType() {
		return type;
	}

	@Override
	public String toString() {
		return "id:" + id + ",url:" + url + ",ctx:" + contextWindow + ",type:" + type.name();
	}

}
