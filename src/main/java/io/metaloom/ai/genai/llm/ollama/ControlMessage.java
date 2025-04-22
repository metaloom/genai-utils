package io.metaloom.ai.genai.llm.ollama;

import java.util.Map;

import dev.langchain4j.data.message.CustomMessage;

public class ControlMessage extends CustomMessage {

	public ControlMessage() {
		super(Map.of("role", "control", "content", "thinking"));
	}

}
