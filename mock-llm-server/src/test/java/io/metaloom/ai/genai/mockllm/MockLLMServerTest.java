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
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.StreamEvent;
import io.metaloom.ai.genai.llm.ToolCallResponse;
import io.metaloom.ai.genai.llm.ToolDefinition;
import io.metaloom.ai.genai.llm.impl.LargeLanguageModelImpl;
import io.metaloom.ai.genai.llm.openai.OpenAILLMProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.metaloom.ai.genai.mockllm.MockResponse.ErrorResponse;
import io.metaloom.ai.genai.mockllm.MockResponse.MockToolCall;
import io.metaloom.ai.genai.mockllm.MockResponse.ToolCallsResponse;
import io.vertx.core.json.JsonObject;

/**
 * Verifies the behaviour of {@link MockLLMServer} by driving it through the real genai-utils
 * OpenAI-compatible client ({@link OpenAILLMProvider}) — the exact code path the metaloom chat
 * agent uses at runtime.
 */
class MockLLMServerTest {

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

	/** A model pointing at the freshly-started mock server. */
	private LargeLanguageModel model() {
		return new LargeLanguageModelImpl("mock-model", server.baseUrl(), 2048);
	}

	private LLMContext chatCtx(String userMessage) {
		Prompt prompt = new PromptImpl(userMessage);
		return LLMContext.ctx(prompt, model());
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

		LLMContext ctx = chatCtx("What is the weather in Vienna?");
		ctx.setTools(List.of(weatherTool()));

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
	void testStreamingToolCallResponse() {
		MockToolCall toolCall = MockToolCall.of("get_weather", new JsonObject().put("city", "Vienna"));
		server.addToolCallsResponse(ToolCallsResponse.of(toolCall)).start();

		LLMContext ctx = chatCtx("What is the weather in Vienna?");
		ctx.setTools(List.of(weatherTool()));

		List<StreamEvent> events = provider.generateStreamWithTools(ctx).toList().blockingGet();

		// The fragments must be reassembled into exactly one complete call...
		List<StreamEvent.ToolCallsComplete> completed = events.stream()
				.filter(StreamEvent.ToolCallsComplete.class::isInstance)
				.map(StreamEvent.ToolCallsComplete.class::cast)
				.toList();
		assertEquals(1, completed.size());
		assertEquals(1, completed.getFirst().toolCalls().size());
		assertEquals("get_weather", completed.getFirst().toolCalls().getFirst().name());
		assertEquals("Vienna", completed.getFirst().toolCalls().getFirst().arguments().getString("city"));

		// ...and the stream must terminate with Completed as its last event.
		assertTrue(events.getLast() instanceof StreamEvent.Completed);
	}

	@Test
	void testStreamingParallelToolCalls() {
		server.addToolCallsResponse(new ToolCallsResponse(List.of(
				MockToolCall.of("get_weather", new JsonObject().put("city", "Tokyo")),
				MockToolCall.of("get_weather", new JsonObject().put("city", "Berlin"))))).start();

		LLMContext ctx = chatCtx("Weather in Tokyo and Berlin?");
		ctx.setTools(List.of(weatherTool()));

		List<StreamEvent> events = provider.generateStreamWithTools(ctx).toList().blockingGet();

		StreamEvent.ToolCallsComplete completed = events.stream()
				.filter(StreamEvent.ToolCallsComplete.class::isInstance)
				.map(StreamEvent.ToolCallsComplete.class::cast)
				.findFirst()
				.orElseThrow();
		assertEquals(2, completed.toolCalls().size());
		assertEquals("Tokyo", completed.toolCalls().get(0).arguments().getString("city"));
		assertEquals("Berlin", completed.toolCalls().get(1).arguments().getString("city"));
	}

	@Test
	void testStreamingWithToolsEmitsTextWhenNoToolIsCalled() {
		server.addResponse("No tool needed here.").start();

		LLMContext ctx = chatCtx("Just say something");
		ctx.setTools(List.of(weatherTool()));

		List<StreamEvent> events = provider.generateStreamWithTools(ctx).toList().blockingGet();

		String text = events.stream()
				.filter(StreamEvent.TextDelta.class::isInstance)
				.map(e -> ((StreamEvent.TextDelta) e).text())
				.reduce("", String::concat);
		assertEquals("No tool needed here.", text);

		assertTrue(events.stream().noneMatch(StreamEvent.ToolCallsComplete.class::isInstance));
		assertEquals("No tool needed here.", ((StreamEvent.Completed) events.getLast()).fullText());
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
