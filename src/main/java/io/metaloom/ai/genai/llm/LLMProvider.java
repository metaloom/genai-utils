package io.metaloom.ai.genai.llm;

import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.json.JsonObject;

public interface LLMProvider {

	String generate(LLMContext ctx);

	JsonObject generateJson(LLMContext ctx);

	Flowable<Chunk> generateStream(LLMContext ctx);

	LLMProviderType type();
}
