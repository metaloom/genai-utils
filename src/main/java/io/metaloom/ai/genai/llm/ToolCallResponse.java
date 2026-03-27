package io.metaloom.ai.genai.llm;

import java.util.List;

/**
 * Response from an LLM that may contain tool calls and/or text content.
 */
public class ToolCallResponse {

	private final String content;
	private final List<ToolCall> toolCalls;

	public ToolCallResponse(String content, List<ToolCall> toolCalls) {
		this.content = content;
		this.toolCalls = toolCalls;
	}

	/**
	 * Optional text content returned alongside tool calls. May be null.
	 *
	 * @return
	 */
	public String content() {
		return content;
	}

	/**
	 * List of tool calls the LLM wants to make. Empty if the model chose not to call any tools.
	 *
	 * @return
	 */
	public List<ToolCall> toolCalls() {
		return toolCalls;
	}

	/**
	 * Check whether the response contains any tool calls.
	 *
	 * @return
	 */
	public boolean hasToolCalls() {
		return toolCalls != null && !toolCalls.isEmpty();
	}

	@Override
	public String toString() {
		return "ToolCallResponse[content=" + (content != null ? content.substring(0, Math.min(content.length(), 50)) : "null")
			+ ", toolCalls=" + toolCalls + "]";
	}
}
