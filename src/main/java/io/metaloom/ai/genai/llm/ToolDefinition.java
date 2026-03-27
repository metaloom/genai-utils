package io.metaloom.ai.genai.llm;

import io.vertx.core.json.JsonObject;

/**
 * Defines a tool/function that the LLM can invoke.
 */
public class ToolDefinition {

	private final String name;
	private final String description;
	private final JsonObject parameters;

	/**
	 * Create a new tool definition.
	 *
	 * @param name
	 *            The name of the function
	 * @param description
	 *            A description of what the function does
	 * @param parameters
	 *            JSON Schema describing the function parameters
	 */
	public ToolDefinition(String name, String description, JsonObject parameters) {
		this.name = name;
		this.description = description;
		this.parameters = parameters;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	/**
	 * JSON Schema object describing the function parameters.
	 *
	 * @return
	 */
	public JsonObject parameters() {
		return parameters;
	}

	@Override
	public String toString() {
		return "ToolDefinition[name=" + name + "]";
	}
}
