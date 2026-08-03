package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.LargeLanguageModel;

public class LargeLanguageModelImpl implements LargeLanguageModel {

	private String id;
	private String url;
	private long contextWindow;

	public LargeLanguageModelImpl(String id, String url, int contextWindow) {
		this.id = id;
		this.url = url;
		this.contextWindow = contextWindow;
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
	public long contextWindow() {
		return contextWindow;
	}

	@Override
	public String toString() {
		return "id:" + id + ",url:" + url + ",ctx:" + contextWindow;
	}

}
