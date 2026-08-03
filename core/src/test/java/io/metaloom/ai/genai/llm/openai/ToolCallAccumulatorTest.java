package io.metaloom.ai.genai.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;

import io.metaloom.ai.genai.llm.ToolCall;
import io.metaloom.ai.genai.llm.openai.OpenAILLMProvider.ToolCallAccumulator;

/**
 * Verifies that the tool_calls fragments of a streamed OpenAI response are reassembled into whole
 * tool calls, without a live LLM.
 */
public class ToolCallAccumulatorTest {

	@Test
	public void testSingleToolCallSplitOverChunks() {
		ToolCallAccumulator accumulator = new ToolCallAccumulator();

		// A server announces id and name first, then dribbles the argument JSON.
		accumulator.accept(List.of(delta(0, "call_1", "get_current_weather", null)));
		accumulator.accept(List.of(delta(0, null, null, "{\"loca")));
		accumulator.accept(List.of(delta(0, null, null, "tion\":\"Ber")));
		accumulator.accept(List.of(delta(0, null, null, "lin\"}")));

		List<ToolCall> calls = accumulator.toToolCalls();
		assertEquals(1, calls.size());
		ToolCall call = calls.getFirst();
		assertEquals("call_1", call.id());
		assertEquals("get_current_weather", call.name());
		assertEquals("Berlin", call.arguments().getString("location"));
	}

	@Test
	public void testParallelToolCallsKeptApartByIndex() {
		ToolCallAccumulator accumulator = new ToolCallAccumulator();

		// Interleaved fragments of two calls — only the index tells them apart.
		accumulator.accept(List.of(
			delta(0, "call_a", "get_current_weather", "{\"location\":"),
			delta(1, "call_b", "convert_currency", "{\"amount\":")));
		accumulator.accept(List.of(
			delta(0, null, null, "\"Tokyo\"}"),
			delta(1, null, null, "50}")));

		List<ToolCall> calls = accumulator.toToolCalls();
		assertEquals(2, calls.size());

		assertEquals("call_a", calls.get(0).id());
		assertEquals("get_current_weather", calls.get(0).name());
		assertEquals("Tokyo", calls.get(0).arguments().getString("location"));

		assertEquals("call_b", calls.get(1).id());
		assertEquals("convert_currency", calls.get(1).name());
		assertEquals(50, calls.get(1).arguments().getInteger("amount"));
	}

	@Test
	public void testToolCallWithoutArgumentsYieldsEmptyObject() {
		ToolCallAccumulator accumulator = new ToolCallAccumulator();
		accumulator.accept(List.of(delta(0, "call_1", "list_assets", null)));

		List<ToolCall> calls = accumulator.toToolCalls();
		assertEquals(1, calls.size());
		assertTrue(calls.getFirst().arguments().isEmpty());
	}

	@Test
	public void testFragmentWithoutFunctionNameIsDiscarded() {
		ToolCallAccumulator accumulator = new ToolCallAccumulator();
		accumulator.accept(List.of(delta(0, "call_1", null, "{\"a\":1}")));

		assertTrue(accumulator.toToolCalls().isEmpty());
	}

	@Test
	public void testNothingStreamedYieldsNoCalls() {
		assertTrue(new ToolCallAccumulator().toToolCalls().isEmpty());
	}

	@Test
	public void testMissingIdIsToleratedByServersThatOmitIt() {
		ToolCallAccumulator accumulator = new ToolCallAccumulator();
		accumulator.accept(List.of(delta(0, null, "list_assets", "{}")));

		List<ToolCall> calls = accumulator.toToolCalls();
		assertEquals(1, calls.size());
		assertNull(calls.getFirst().id());
	}

	private static Delta.ToolCall delta(long index, String id, String name, String arguments) {
		Delta.ToolCall.Builder builder = Delta.ToolCall.builder().index(index);
		if (id != null) {
			builder.id(id);
		}
		if (name != null || arguments != null) {
			Delta.ToolCall.Function.Builder function = Delta.ToolCall.Function.builder();
			if (name != null) {
				function.name(name);
			}
			if (arguments != null) {
				function.arguments(arguments);
			}
			builder.function(function.build());
		}
		return builder.build();
	}
}
