package io.metaloom.ai.genai.llm;

import java.util.List;

/**
 * Typed event of a streaming LLM call with tool support (see {@link LLMProvider#generateStreamWithTools(LLMContext)}).
 */
public sealed interface StreamEvent {

	/**
	 * A fragment of the response text.
	 */
	record TextDelta(String text) implements StreamEvent {
	}

	/**
	 * A fragment of the model reasoning ("thinking").
	 */
	record ReasoningDelta(String text) implements StreamEvent {
	}

	/**
	 * The model requested tool calls. Emitted once all tool calls of the response are complete.
	 */
	record ToolCallsComplete(List<ToolCall> toolCalls) implements StreamEvent {
	}

	/**
	 * The call finished. Carries the full response text (may be null when the model only produced tool calls).
	 */
	record Completed(String fullText) implements StreamEvent {
	}

}
