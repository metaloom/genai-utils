package io.metaloom.ai.genai.llm.omni;

import java.util.List;
import java.util.Objects;

import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LLMProviderType;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;

public class OmniProvider implements LLMProvider {

	private List<LLMProvider> providers;

	public OmniProvider(LLMProvider... providers) {
		this.providers = List.of(providers);
	}

	@Override
	public String generate(LLMContext ctx) {
		return selectProvider(ctx).generate(ctx);
	}

	@Override
	public JsonObject generateJson(LLMContext ctx) {
		return selectProvider(ctx).generateJson(ctx);
	}

	@Override
	public Flowable<Chunk> generateStream(LLMContext ctx) {
		return selectProvider(ctx).generateStream(ctx);
	}

	LLMProvider selectProvider(LLMContext ctx) {
		Objects.requireNonNull(ctx, "Context not set");
		for (LLMProvider provider : providers) {
			if (provider.type() == ctx.model().providerType()) {
				return provider;
			}
		}
		throw new RuntimeException("Unable to find suitable provider for LLM " + ctx.model());
	}

	@Override
	public LLMProviderType type() {
		return null;
	}

}
