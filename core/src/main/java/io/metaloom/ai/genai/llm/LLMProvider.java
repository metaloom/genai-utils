package io.metaloom.ai.genai.llm;

import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.json.JsonObject;

public interface LLMProvider {

	String generate(LLMContext ctx);

	JsonObject generateJson(LLMContext ctx);

	/**
	 * Generate a response and return it together with the token accounting the server reported for
	 * the call ({@code usage} of the OpenAI-compatible response body).
	 *
	 * <p>
	 * The default implementation falls back to {@link #generate(LLMContext)} and reports
	 * {@link TokenUsage#NONE}; providers that can read the server usage override it.
	 *
	 * @param ctx
	 * @return response containing the generated text and the token usage
	 */
	default LLMResponse generateResponse(LLMContext ctx) {
		return new LLMResponse(generate(ctx), TokenUsage.NONE);
	}

	/**
	 * Generate a streamed response. When the server reports token usage (see
	 * {@link LLMContext#isStreamUsageEnabled()}) a final, empty chunk carrying the accounting is
	 * appended to the stream - see {@link Chunk#usage()}.
	 *
	 * @param ctx
	 * @return stream of chunks
	 */
	Flowable<Chunk> generateStream(LLMContext ctx);

	/**
	 * Generate a response that may include tool/function calls.
	 * The tools to be offered to the LLM are taken from {@link LLMContext#tools()}.
	 *
	 * @param ctx
	 * @return response containing optional text content and/or tool calls
	 */
	ToolCallResponse generateWithTools(LLMContext ctx);

	/**
	 * Generate a streamed response that may include tool/function calls. Text and reasoning fragments are emitted as they arrive; tool calls are emitted once
	 * complete via {@link StreamEvent.ToolCallsComplete}. The stream terminates with {@link StreamEvent.Completed}.
	 * The tools to be offered to the LLM are taken from {@link LLMContext#tools()}.
	 *
	 * @param ctx
	 * @return stream of typed events
	 */
	Flowable<StreamEvent> generateStreamWithTools(LLMContext ctx);
}
