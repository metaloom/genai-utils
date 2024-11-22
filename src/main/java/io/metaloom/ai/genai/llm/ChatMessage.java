package io.metaloom.ai.genai.llm;

import io.metaloom.ai.genai.llm.impl.ChatMessageImpl;

public interface ChatMessage {
	String getText();

	String getRole();

	public static ChatMessage user(String text) {
		return new ChatMessageImpl(text, "user");
	}

	public static ChatMessage assistant(String text) {
		return new ChatMessageImpl(text, "role");
	}
}
