package io.metaloom.ai.genai.mockllm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.ReasoningLevel;
import io.metaloom.ai.genai.llm.StreamEvent;
import io.metaloom.ai.genai.llm.ToolDefinition;
import io.metaloom.ai.genai.llm.impl.LargeLanguageModelImpl;
import io.metaloom.ai.genai.llm.openai.OpenAILLMProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.metaloom.ai.genai.mockllm.MockResponse.MockToolCall;
import io.metaloom.ai.genai.mockllm.MockResponse.ToolCallsResponse;
import io.vertx.core.json.JsonObject;

/**
 * Asserts what the {@link OpenAILLMProvider} puts on the wire for a configured
 * {@link ReasoningLevel} - the {@code reasoning_effort} field OpenAI reads and the
 * {@code chat_template_kwargs} llama.cpp and vLLM hand to the chat template.
 */
class ReasoningLevelWireTest {

	private MockLLMServer server;

	private final LLMProvider provider = new OpenAILLMProvider();

	@BeforeEach
	void setUp() {
		server = MockLLMServer.create(0);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void testUnsetSendsNothing() {
		server.addResponse("Hello").start();

		provider.generate(ctx("Hi"));

		JsonObject request = server.lastRequest();
		assertNull(request.getValue("reasoning_effort"), "An unset level must not send an effort");
		assertNull(request.getValue("chat_template_kwargs"), "An unset level must leave the server at its default");
	}

	@Test
	void testEnableThinkSendsThinkingFlagOnly() {
		server.addResponse("Hello").start();

		provider.generate(ctx("Hi").enableThink());

		JsonObject request = server.lastRequest();
		assertNull(request.getValue("reasoning_effort"), "ON states no effort");
		assertEquals(new JsonObject().put("enable_thinking", true), request.getJsonObject("chat_template_kwargs"));
	}

	@Test
	void testLevelSendsEffortInBothPlaces() {
		server.addResponse("Hello").start();

		provider.generate(ctx("Hi").reasoning(ReasoningLevel.XHIGH));

		JsonObject request = server.lastRequest();
		assertEquals("xhigh", request.getString("reasoning_effort"));
		JsonObject kwargs = request.getJsonObject("chat_template_kwargs");
		assertEquals(true, kwargs.getBoolean("enable_thinking"));
		assertEquals("xhigh", kwargs.getString("reasoning_effort"));
	}

	@Test
	void testOffDisablesThinking() {
		server.addResponse("Hello").start();

		provider.generate(ctx("Hi").reasoning(ReasoningLevel.OFF));

		JsonObject request = server.lastRequest();
		assertEquals("none", request.getString("reasoning_effort"));
		JsonObject kwargs = request.getJsonObject("chat_template_kwargs");
		assertEquals(false, kwargs.getBoolean("enable_thinking"));
		assertEquals("none", kwargs.getString("reasoning_effort"));
	}

	@Test
	void testStreamCarriesTheLevel() {
		server.addResponse("Hello").start();

		provider.generateStream(ctx("Hi").reasoning(ReasoningLevel.LOW)).blockingSubscribe(chunk -> {
		});

		JsonObject request = server.lastRequest();
		assertTrue(request.getBoolean("stream"));
		assertEquals("low", request.getString("reasoning_effort"));
		assertEquals("low", request.getJsonObject("chat_template_kwargs").getString("reasoning_effort"));
	}

	@Test
	void testToolCallsCarryTheLevel() {
		server.addToolCallsResponse(ToolCallsResponse.of(MockToolCall.of("get_weather", new JsonObject().put("city", "Vienna"))))
				.start();

		LLMContext ctx = ctx("What is the weather in Vienna?").reasoning(ReasoningLevel.MEDIUM);
		ctx.setTools(List.of(weatherTool()));
		provider.generateWithTools(ctx);

		JsonObject request = server.lastRequest();
		assertEquals("medium", request.getString("reasoning_effort"));
		assertEquals("medium", request.getJsonObject("chat_template_kwargs").getString("reasoning_effort"));
	}

	@Test
	void testStreamedToolCallsCarryTheLevel() {
		server.addToolCallsResponse(ToolCallsResponse.of(MockToolCall.of("get_weather", new JsonObject().put("city", "Vienna"))))
				.start();

		LLMContext ctx = ctx("What is the weather in Vienna?").reasoning(ReasoningLevel.MEDIUM);
		ctx.setTools(List.of(weatherTool()));
		List<StreamEvent> events = provider.generateStreamWithTools(ctx).toList().blockingGet();
		assertFalse(events.isEmpty());

		JsonObject request = server.lastRequest();
		assertNotNull(request);
		assertEquals("medium", request.getString("reasoning_effort"));
		assertEquals("medium", request.getJsonObject("chat_template_kwargs").getString("reasoning_effort"));
	}

	private LLMContext ctx(String userMessage) {
		Prompt prompt = new PromptImpl(userMessage);
		LargeLanguageModel model = new LargeLanguageModelImpl("mock-model", server.baseUrl(), 2048);
		return LLMContext.ctx(prompt, model);
	}

	private static ToolDefinition weatherTool() {
		return new ToolDefinition(
				"get_weather",
				"Get the current weather for a city",
				new JsonObject()
						.put("type", "object")
						.put("properties", new JsonObject()
								.put("city", new JsonObject().put("type", "string")))
						.put("required", List.of("city")));
	}
}
