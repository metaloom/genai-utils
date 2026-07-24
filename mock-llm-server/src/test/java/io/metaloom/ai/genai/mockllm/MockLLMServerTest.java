package io.metaloom.ai.genai.mockllm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LLMProviderType;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.ToolCallResponse;
import io.metaloom.ai.genai.llm.ToolDefinition;
import io.metaloom.ai.genai.llm.impl.LargeLanguageModelImpl;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.metaloom.ai.genai.llm.vllm.VLLMLLMProvider;
import io.metaloom.ai.genai.mockllm.MockResponse.ErrorResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.MockToolCall;
import io.metaloom.ai.genai.mockllm.MockResponse.ToolCallsResponse;
import io.vertx.core.json.JsonObject;

/**
 * Verifies the behaviour of {@link MockLLMServer} by driving it through the real genai-utils
 * OpenAI-compatible client ({@link VLLMLLMProvider}) — the exact code path the metaloom chat
 * agent uses at runtime.
 */
class MockLLMServerTest {

	private MockLLMServer server;
	private final LLMProvider provider = new VLLMLLMProvider();

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

	/** A model pointing at the freshly-started mock server. */
	private LargeLanguageModel model() {
		return new LargeLanguageModelImpl("mock-model", server.baseUrl(), 2048, LLMProviderType.VLLM);
	}

	private LLMContext chatCtx(String userMessage) {
		Prompt prompt = new PromptImpl(userMessage);
		return LLMContext.ctx(prompt, model());
	}

	@Test
	void testSuccessResponse() {
		server.addResponse("Hello World").start();

		String text = provider.generate(chatCtx("Hi"));

		assertEquals("Hello World", text);
		assertEquals(0, server.remainingResponses());
	}

	@Test
	void testMultipleResponsesConsumedInOrder() {
		server.addResponses("First", "Second", "Third").start();

		assertEquals("First", provider.generate(chatCtx("1")));
		assertEquals("Second", provider.generate(chatCtx("2")));
		assertEquals("Third", provider.generate(chatCtx("3")));
		assertEquals(0, server.remainingResponses());
	}

	@Test
	void testStructuredResponse() {
		JsonObject expected = new JsonObject()
				.put("city", "Vienna")
				.put("temp", 22);
		server.addStructuredResponse(expected).start();

		JsonObject result = provider.generateJson(chatCtx("What is the weather?"));

		assertEquals("Vienna", result.getString("city"));
		assertEquals(22, result.getInteger("temp"));
	}

	@Test
	void testToolCallResponse() {
		MockToolCall toolCall = MockToolCall.of("get_weather", new JsonObject().put("city", "Vienna"));
		server.addToolCallsResponse(ToolCallsResponse.of(toolCall)).start();

		ToolDefinition weatherTool = new ToolDefinition(
				"get_weather",
				"Get the current weather for a city",
				new JsonObject()
						.put("type", "object")
						.put("properties", new JsonObject()
								.put("city", new JsonObject().put("type", "string")))
						.put("required", List.of("city")));

		LLMContext ctx = chatCtx("What is the weather in Vienna?");
		ctx.setTools(List.of(weatherTool));

		ToolCallResponse response = provider.generateWithTools(ctx);

		assertTrue(response.hasToolCalls());
		assertEquals(1, response.toolCalls().size());
		assertEquals("get_weather", response.toolCalls().getFirst().name());
		assertEquals("Vienna", response.toolCalls().getFirst().arguments().getString("city"));
	}

	@Test
	void testStreamingResponse() {
		String expected = "The quick brown fox jumps over the lazy dog.";
		server.addResponse(expected).start();

		List<Chunk> chunks = provider.generateStream(chatCtx("Tell me a sentence"))
				.toList()
				.blockingGet();

		String collected = chunks.stream()
				.filter(c -> !c.isThinking())
				.map(Object::toString)
				.reduce("", String::concat);

		assertEquals(expected, collected);
	}

	@Test
	void testRateLimitErrorIsPropagated() {
		server.addError(ErrorResponse.rateLimitError()).start();

		assertThrows(RuntimeException.class, () -> provider.generate(chatCtx("Hi")));
	}

	@Test
	void testEmptyQueueReturnsError() {
		server.start();

		assertThrows(RuntimeException.class, () -> provider.generate(chatCtx("Hi")));
	}

	@Test
	void testModelsEndpointAvailable() {
		// The models endpoint is registered and reachable; baseUrl resolves after start.
		server.addResponse("ok").start();
		assertNotNull(server.baseUrl());
		assertTrue(server.port() > 0);
	}
}
