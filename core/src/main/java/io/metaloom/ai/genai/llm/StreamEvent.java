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
	 * The call finished. Carries the full response text (may be null when the model only produced tool calls) and the token accounting the server reported for
	 * the call. The usage is {@link TokenUsage#NONE} when the server did not report any - see {@link LLMContext#isStreamUsageEnabled()}.
	 */
	record Completed(String fullText, TokenUsage usage) implements StreamEvent {

		public Completed {
			if (usage == null) {
				usage = TokenUsage.NONE;
			}
		}

		public Completed(String fullText) {
			this(fullText, TokenUsage.NONE);
		}
	}

}
