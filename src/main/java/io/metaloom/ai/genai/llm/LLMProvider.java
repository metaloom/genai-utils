package io.metaloom.ai.genai.llm;

import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.json.JsonObject;

public interface LLMProvider {

	String generate(LLMContext ctx);

	JsonObject generateJson(LLMContext ctx);

	Flowable<Chunk> generateStream(LLMContext ctx);

	/**
	 * Generate a response that may include tool/function calls.
	 * The tools to be offered to the LLM are taken from {@link LLMContext#tools()}.
	 *
	 * @param ctx
	 * @return response containing optional text content and/or tool calls
	 */
	ToolCallResponse generateWithTools(LLMContext ctx);

	LLMProviderType type();
}
