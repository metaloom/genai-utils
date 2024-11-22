package io.metaloom.ai.genai.llm;

import java.util.ArrayList;
import java.util.List;

import io.metaloom.ai.genai.llm.impl.LLMContextImpl;

public abstract class AbstractLLMContext implements LLMContext {

	private double temperature = 0.35f;

	private int tokenOutputLimit = 2048;

	private Integer seed = null;

	private LargeLanguageModel llm;

	private List<? extends ChatMessage> chatHistory = new ArrayList<>();

	public AbstractLLMContext(List<? extends ChatMessage> chatHistory, LargeLanguageModel model) {
		this.chatHistory = chatHistory;
		this.llm = model;
	}

	@Override
	public Integer seed() {
		return seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	@Override
	public double temperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	@Override
	public int tokenOutputLimit() {
		return tokenOutputLimit;
	}

	public void setTokenOutputLimit(int tokenOutputLimit) {
		this.tokenOutputLimit = tokenOutputLimit;
	}

	@Override
	public LargeLanguageModel model() {
		return llm;
	}

	public void setModel(LargeLanguageModel llm) {
		this.llm = llm;
	}

	@Override
	public List<? extends ChatMessage> chatHistory() {
		return chatHistory;
	}

	public void setChatHistory(List<? extends ChatMessage> chatHistory) {
		this.chatHistory = chatHistory;
	}

	@Override
	public String toString() {
		return "[msgs:" + chatHistory().size() + ",limit:" + tokenOutputLimit + ",temp:" + temperature + ",model:" + llm.id() + ",seed:" + seed + "]";
	}

	public static LLMContext ctx(List<? extends ChatMessage> history, LargeLanguageModel model) {
		return new LLMContextImpl(history, model);
	}

}
