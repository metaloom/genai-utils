package io.metaloom.ai.genai.llm.impl;

import java.util.List;

import io.metaloom.ai.genai.llm.AbstractLLMContext;
import io.metaloom.ai.genai.llm.ChatMessage;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.prompt.Prompt;

public class LLMContextImpl extends AbstractLLMContext {

	public LLMContextImpl(List<? extends ChatMessage> chatHistory, LargeLanguageModel model, Prompt prompt) {
		super(chatHistory, model, prompt);
	}
}
