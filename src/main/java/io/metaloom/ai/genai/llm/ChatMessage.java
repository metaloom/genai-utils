package io.metaloom.ai.genai.llm;

import java.util.Collections;
import java.util.List;

import io.metaloom.ai.genai.llm.impl.ChatMessageImpl;

public interface ChatMessage {
	String getText();

	String getRole();

	/**
	 * Tool calls associated with this message (for assistant messages).
	 * 
	 * @return list of tool calls, empty by default
	 */
	default List<ToolCall> getToolCalls() {
		return Collections.emptyList();
	}

	/**
	 * Tool call ID this message is responding to (for tool result messages).
	 * 
	 * @return tool call id, or null
	 */
	default String getToolCallId() {
		return null;
	}

	/**
	 * Tool name this message is responding to (for tool result messages).
	 * 
	 * @return tool name, or null
	 */
	default String getToolName() {
		return null;
	}

	public static ChatMessage user(String text) {
		return new ChatMessageImpl(text, "user");
	}

	public static ChatMessage assistant(String text) {
		return new ChatMessageImpl(text, "assistant");
	}

	/**
	 * Create an assistant message that carries tool calls (no text content).
	 */
	public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
		return new ChatMessageImpl(null, "assistant", toolCalls, null, null);
	}

	/**
	 * Create a tool result message.
	 * 
	 * @param toolCallId
	 *            the id of the tool call being responded to
	 * @param toolName
	 *            the name of the tool
	 * @param result
	 *            the result text
	 */
	public static ChatMessage toolResult(String toolCallId, String toolName, String result) {
		return new ChatMessageImpl(result, "tool", Collections.emptyList(), toolCallId, toolName);
	}

	public static ChatMessage system(String text) {
		return new ChatMessageImpl(text, "system");
	}
}
