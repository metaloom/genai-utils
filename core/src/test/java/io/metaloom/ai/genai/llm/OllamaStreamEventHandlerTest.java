package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.metaloom.ai.genai.llm.ollama.OllamaLLMProvider;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Verifies the mapping of langchain4j streaming callbacks onto {@link StreamEvent}s without a live LLM.
 */
public class OllamaStreamEventHandlerTest {

	private List<StreamEvent> record(java.util.function.Consumer<StreamingChatResponseHandler> script) {
		return Flowable.<StreamEvent>create(sub -> {
			script.accept(streamEventHandler(sub));
		}, BackpressureStrategy.BUFFER).toList().blockingGet();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static StreamingChatResponseHandler streamEventHandler(io.reactivex.rxjava3.core.FlowableEmitter<StreamEvent> sub) {
		try {
			var method = OllamaLLMProvider.class.getDeclaredMethod("streamEventHandler", io.reactivex.rxjava3.core.FlowableEmitter.class);
			method.setAccessible(true);
			return (StreamingChatResponseHandler) method.invoke(null, sub);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testTextAndReasoningDeltas() {
		List<StreamEvent> events = record(handler -> {
			handler.onPartialThinking(new PartialThinking("thinking about it"));
			handler.onPartialResponse("Hello ");
			handler.onPartialResponse("world");
			handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("Hello world")).build());
		});

		assertEquals(4, events.size());
		assertEquals("thinking about it", ((StreamEvent.ReasoningDelta) events.get(0)).text());
		assertEquals("Hello ", ((StreamEvent.TextDelta) events.get(1)).text());
		assertEquals("world", ((StreamEvent.TextDelta) events.get(2)).text());
		assertEquals("Hello world", ((StreamEvent.Completed) events.get(3)).fullText());
	}

	@Test
	public void testToolCallCompletion() {
		ToolExecutionRequest request = ToolExecutionRequest.builder()
			.id("c1")
			.name("search_assets")
			.arguments("{\"query\":\"beach\"}")
			.build();

		List<StreamEvent> events = record(handler -> {
			handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(List.of(request))).build());
		});

		assertEquals(2, events.size());
		StreamEvent.ToolCallsComplete toolCalls = assertInstanceOf(StreamEvent.ToolCallsComplete.class, events.get(0));
		assertEquals(1, toolCalls.toolCalls().size());
		ToolCall call = toolCalls.toolCalls().get(0);
		assertEquals("c1", call.id());
		assertEquals("search_assets", call.name());
		assertEquals("beach", call.arguments().getString("query"));
		assertInstanceOf(StreamEvent.Completed.class, events.get(1));
	}

	@Test
	public void testErrorPropagation() {
		var result = Flowable.<StreamEvent>create(sub -> {
			streamEventHandler(sub).onError(new IllegalStateException("provider down"));
		}, BackpressureStrategy.BUFFER).toList()
			.map(l -> "ok")
			.onErrorReturn(Throwable::getMessage)
			.blockingGet();
		assertTrue(result.contains("provider down"));
	}

}
