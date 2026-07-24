package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.metaloom.ai.genai.llm.vllm.VLLMLLMProvider;
import io.vertx.core.json.JsonObject;

public class VLLMToolCallTest {

	@Test
	public void testToolCall() {
		LargeLanguageModel model = VLLMTestModel.mistral24bQ8("http://localhost:11436/v1");
		Prompt prompt = new PromptImpl("What is the current weather in Berlin?");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		JsonObject parameters = new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("location", new JsonObject()
					.put("type", "string")
					.put("description", "The city name"))
				.put("unit", new JsonObject()
					.put("type", "string")
					.put("description", "Temperature unit (celsius or fahrenheit)")))
			.put("required", new io.vertx.core.json.JsonArray().add("location"));

		ToolDefinition weatherTool = new ToolDefinition(
			"get_current_weather",
			"Get the current weather for a given location",
			parameters);

		ctx.setTools(List.of(weatherTool));

		LLMProvider provider = new VLLMLLMProvider();
		ToolCallResponse response = provider.generateWithTools(ctx);
		assertNotNull(response);
		System.out.println("Content: " + response.content());
		System.out.println("Tool calls: " + response.toolCalls());
		assertTrue(response.hasToolCalls(), "Expected at least one tool call");
		assertFalse(response.toolCalls().isEmpty());

		ToolCall call = response.toolCalls().getFirst();
		System.out.println("Tool call name: " + call.name());
		System.out.println("Tool call args: " + call.arguments());
		assertNotNull(call.name());
		assertNotNull(call.arguments());
	}

	@Test
	public void testMultipleTools() {
		LargeLanguageModel model = VLLMTestModel.mistral24bQ8("http://localhost:11436/v1");
		Prompt prompt = new PromptImpl("Look up the weather in Tokyo and convert 50 EUR to JPY");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		JsonObject weatherParams = new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("location", new JsonObject()
					.put("type", "string")
					.put("description", "The city name")))
			.put("required", new io.vertx.core.json.JsonArray().add("location"));

		JsonObject currencyParams = new JsonObject()
			.put("type", "object")
			.put("properties", new JsonObject()
				.put("amount", new JsonObject()
					.put("type", "number")
					.put("description", "The amount to convert"))
				.put("from", new JsonObject()
					.put("type", "string")
					.put("description", "Source currency code"))
				.put("to", new JsonObject()
					.put("type", "string")
					.put("description", "Target currency code")))
			.put("required", new io.vertx.core.json.JsonArray().add("amount").add("from").add("to"));

		ctx.setTools(List.of(
			new ToolDefinition("get_current_weather", "Get the current weather for a location", weatherParams),
			new ToolDefinition("convert_currency", "Convert an amount between currencies", currencyParams)));

		LLMProvider provider = new VLLMLLMProvider();
		ToolCallResponse response = provider.generateWithTools(ctx);
		assertNotNull(response);
		System.out.println("Content: " + response.content());
		System.out.println("Tool calls: " + response.toolCalls());
		for (ToolCall call : response.toolCalls()) {
			System.out.println("  -> " + call.name() + " " + call.arguments());
		}
	}
}
