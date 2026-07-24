package io.metaloom.ai.genai.llm;

import io.vertx.core.json.JsonObject;

/**
 * Represents a tool/function call requested by the LLM.
 */
public class ToolCall {

	private final String id;
	private final String name;
	private final JsonObject arguments;

	/**
	 * Create a new tool call.
	 *
	 * @param id
	 *            The tool call ID (used to correlate responses)
	 * @param name
	 *            The name of the function to call
	 * @param arguments
	 *            The arguments the LLM wants to pass to the function
	 */
	public ToolCall(String id, String name, JsonObject arguments) {
		this.id = id;
		this.name = name;
		this.arguments = arguments;
	}

	/**
	 * Tool call identifier. May be null for providers that don't supply one.
	 *
	 * @return
	 */
	public String id() {
		return id;
	}

	/**
	 * Name of the function the LLM wants to invoke.
	 *
	 * @return
	 */
	public String name() {
		return name;
	}

	/**
	 * Arguments for the function call as JSON.
	 *
	 * @return
	 */
	public JsonObject arguments() {
		return arguments;
	}

	@Override
	public String toString() {
		return "ToolCall[id=" + id + ", name=" + name + ", arguments=" + arguments + "]";
	}
}
