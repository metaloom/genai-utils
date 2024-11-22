package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.ChatMessage;

public class ChatMessageImpl implements ChatMessage {

	private final String text;

	private final String role;

	public ChatMessageImpl(String text, String role) {
		this.text = text;
		this.role = role;
	}

	@Override
	public String getRole() {
		return role;
	}

	@Override
	public String getText() {
		return text;
	}

}
