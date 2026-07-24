package io.metaloom.ai.genai.llm.impl;

import java.util.Collections;
import java.util.List;

import io.metaloom.ai.genai.llm.ChatMessage;
import io.metaloom.ai.genai.llm.ToolCall;

public class ChatMessageImpl implements ChatMessage {

	private final String text;

	private final String role;

	private final List<ToolCall> toolCalls;

	private final String toolCallId;

	private final String toolName;

	public ChatMessageImpl(String text, String role) {
		this(text, role, Collections.emptyList(), null, null);
	}

	public ChatMessageImpl(String text, String role, List<ToolCall> toolCalls, String toolCallId, String toolName) {
		this.text = text;
		this.role = role;
		this.toolCalls = toolCalls != null ? toolCalls : Collections.emptyList();
		this.toolCallId = toolCallId;
		this.toolName = toolName;
	}

	@Override
	public String getRole() {
		return role;
	}

	@Override
	public String getText() {
		return text;
	}

	@Override
	public List<ToolCall> getToolCalls() {
		return toolCalls;
	}

	@Override
	public String getToolCallId() {
		return toolCallId;
	}

	@Override
	public String getToolName() {
		return toolName;
	}

}
