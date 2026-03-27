package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.ollama.OllamaLLMProvider;
import io.metaloom.ai.genai.llm.omni.OmniProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.vertx.core.json.JsonObject;

public class OllamaToolCallTest {

	@Test
	public void testToolCall() {
		LargeLanguageModel model = TestModel.OLLAMA_GPT_OSS_20B;
		Prompt prompt = new PromptImpl(
				"What is the current weather in Berlin and who won the history world series 1919?");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		// Define a history tool
		ToolDefinition historyTool = historyTool();
		ToolDefinition weatherTool = weatherTool();

		ctx.setTools(List.of(weatherTool, historyTool));

		LLMProvider provider = new OllamaLLMProvider();
		ToolCallResponse response = provider.generateWithTools(ctx);
		assertNotNull(response);
		System.out.println("Content: " + response.content());
		System.out.println("Tool calls: " + response.toolCalls());
		assertTrue(response.hasToolCalls(), "Expected at least one tool call");
		assertFalse(response.toolCalls().isEmpty());

		for (ToolCall call : response.toolCalls()) {
			System.out.println("Tool call name: " + call.name());
			System.out.println("Tool call args: " + call.arguments());
			assertNotNull(call.name());
			assertNotNull(call.arguments());
		}
	}

	private ToolDefinition weatherTool() {
		// Define a weather tool
		JsonObject weatherToolParameters = new JsonObject()
				.put("type",
						"object")
				.put("properties",
						new JsonObject()
								.put("location",
										new JsonObject().put("type", "string").put("description", "The city name"))
								.put("unit",
										new JsonObject().put("type", "string").put("description",
												"Temperature unit (celsius or fahrenheit)")))
				.put("required", new io.vertx.core.json.JsonArray().add("location"));

		return new ToolDefinition("get_current_weather", "Get the current weather for a given location",
				weatherToolParameters);
	}

	private ToolDefinition historyTool() {
		JsonObject historyToolParameters = new JsonObject().put("type", "object").put("properties",
				new JsonObject().put("topic",
						new JsonObject().put("type", "string").put("description", "Topic to get information on")))
				.put("required", new io.vertx.core.json.JsonArray().add("topic"));

		return new ToolDefinition("get_history_info", "Get history information on a given topic",
				historyToolParameters);
	}

	@Test
	public void testToolCallViaOmni() {
		LargeLanguageModel model = TestModel.OLLAMA_GPT_OSS_20B;
		Prompt prompt = new PromptImpl("What is the current weather in Berlin?");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		JsonObject parameters = new JsonObject().put("type", "object")
				.put("properties",
						new JsonObject().put("location",
								new JsonObject().put("type", "string").put("description", "The city name")))
				.put("required", new io.vertx.core.json.JsonArray().add("location"));

		ctx.setTools(List
				.of(new ToolDefinition("get_current_weather", "Get the current weather for a location", parameters)));

		LLMProvider provider = new OmniProvider(new OllamaLLMProvider());
		ToolCallResponse response = provider.generateWithTools(ctx);
		assertNotNull(response);
		System.out.println("Content: " + response.content());
		System.out.println("Tool calls: " + response.toolCalls());
	}

	@Test
	public void testMultipleTools() {
		LargeLanguageModel model = TestModel.OLLAMA_GPT_OSS_20B;
		Prompt prompt = new PromptImpl("Look up the weather in Paris and convert 100 USD to EUR");
		LLMContext ctx = LLMContext.ctx(prompt, model);

		JsonObject weatherParams = new JsonObject().put("type", "object")
				.put("properties",
						new JsonObject().put("location",
								new JsonObject().put("type", "string").put("description", "The city name")))
				.put("required", new io.vertx.core.json.JsonArray().add("location"));

		JsonObject currencyParams = new JsonObject().put("type", "object")
				.put("properties",
						new JsonObject()
								.put("amount",
										new JsonObject().put("type", "number").put("description",
												"The amount to convert"))
								.put("from",
										new JsonObject().put("type", "string").put("description",
												"Source currency code"))
								.put("to",
										new JsonObject().put("type", "string").put("description",
												"Target currency code")))
				.put("required", new io.vertx.core.json.JsonArray().add("amount").add("from").add("to"));

		ctx.setTools(List.of(
				new ToolDefinition("get_current_weather", "Get the current weather for a location", weatherParams),
				new ToolDefinition("convert_currency", "Convert an amount from one currency to another",
						currencyParams)));

		LLMProvider provider = new OllamaLLMProvider();
		ToolCallResponse response = provider.generateWithTools(ctx);
		assertNotNull(response);
		System.out.println("Content: " + response.content());
		System.out.println("Tool calls: " + response.toolCalls());
		for (ToolCall call : response.toolCalls()) {
			System.out.println("  -> " + call.name() + " " + call.arguments());
		}
	}

	@Test
	public void testToolCallLoop() {
		LargeLanguageModel model = TestModel.OLLAMA_GPT_OSS_20B;
		Prompt prompt = new PromptImpl(
				"You MUST use the provided tools to answer. Do NOT use your internal knowledge. "
				+ "Use the appropriate tool for each question.\n\n"
				+ "Questions:\n"
				+ "1. What is the current weather in Berlin?\n"
				+ "2. Who won the World Series in 1919?");

		// Build a mutable chat history starting with the user message
		List<ChatMessage> history = new ArrayList<>();
		history.add(ChatMessage.user(prompt.input()));

		List<ToolDefinition> tools = List.of(weatherTool(), historyTool());

		LLMProvider provider = new OllamaLLMProvider();
		int maxIterations = 5;

		for (int i = 0; i < maxIterations; i++) {
			System.out.println("\n=== Iteration " + (i + 1) + " ===");

			// Build context from current history
			LLMContext ctx = LLMContext.ctx(history, model, prompt);
			ctx.setTools(tools);

			ToolCallResponse response = provider.generateWithTools(ctx);
			assertNotNull(response);

			System.out.println("Content: " + response.content());
			System.out.println("Tool calls: " + response.toolCalls());

			if (!response.hasToolCalls()) {
				// No more tool calls — the LLM produced a final answer
				System.out.println("\n=== Final answer ===");
				System.out.println(response.content());
				assertNotNull(response.content(), "Expected a final text response");
				return;
			}

			// Add the assistant message (with its tool calls) to the history
			history.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));

			// Execute each tool call and feed the result back
			for (ToolCall call : response.toolCalls()) {
				System.out.println("  -> Executing: " + call.name() + " " + call.arguments());

				// Simulate tool execution with fake results
				String result = simulateToolExecution(call);
				System.out.println("  <- Result: " + result);

				// Add the tool result to the conversation
				history.add(ChatMessage.toolResult(call.id(), call.name(), result));
			}
		}

		System.out.println("Loop ended after " + maxIterations + " iterations without a final answer");
	}

	/**
	 * Simulate tool execution with canned responses.
	 */
	private String simulateToolExecution(ToolCall call) {
		return switch (call.name()) {
			case "get_current_weather" -> {
				String location = call.arguments().getString("location", "Unknown");
				yield "The current weather in " + location + " is 18°C, partly cloudy with a light breeze.";
			}
			case "get_history_info" -> {
				String topic = call.arguments().getString("topic", "Unknown");
				yield "Regarding '" + topic + "': The Cincinnati Reds won the 1919 World Series, "
						+ "which was notably tainted by the Black Sox scandal.";
			}
			default -> "No information available for tool: " + call.name();
		};
	}
}
