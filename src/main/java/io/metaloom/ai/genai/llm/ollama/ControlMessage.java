package io.metaloom.ai.genai.llm.ollama;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;

public class ControlMessage implements ChatMessage {

	private String text;

	public ControlMessage(String text) {
		this.text = text;
	}

	@Override
	public ChatMessageType type() {
		return ChatMessageType.CONTROL;
	}

	@Override
	public String text() {
		return text;
	}

}
