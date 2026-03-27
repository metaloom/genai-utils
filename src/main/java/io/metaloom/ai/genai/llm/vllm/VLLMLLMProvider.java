package io.metaloom.ai.genai.llm.vllm;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletion.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LLMProviderType;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.error.LLMException;
import io.metaloom.ai.genai.llm.impl.ChunkImpl;
import io.metaloom.ai.genai.utils.ReasoningUtils;
import io.metaloom.ai.genai.utils.TextUtils;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.json.JsonObject;

public class VLLMLLMProvider implements LLMProvider {

	private static final Logger logger = LoggerFactory.getLogger(VLLMLLMProvider.class);

	/**
	 * Buffer size for stream response (measured in number of tokens) for the case
	 * where stream consumer is slower than the producer. If the backpressure is
	 * bigger than that,
	 */
	private static final int STREAMING_BUFFER_SIZE = 8192;

	@Override
	public String generate(LLMContext ctx) {
		LargeLanguageModel model = ctx.model();
		OpenAIClient client = buildClient(model.url());

		ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
				.addUserMessage(ctx.prompt().input())
				.temperature(ctx.temperature())
				.model(model.id()).build();
		ChatCompletion chatCompletion = client.chat().completions().create(params);

		Choice firstChoice = chatCompletion.choices().getFirst();
		return firstChoice.message().content().orElseThrow();
	}

	

	@Override
	public JsonObject generateJson(LLMContext ctx) {
		String msg = generate(ctx);
		String jsonStr = TextUtils.extractJson(msg);
		return new JsonObject(jsonStr);
	}

	@Override
	public Flowable<Chunk> generateStream(LLMContext ctx) {
		LargeLanguageModel model = ctx.model();
		OpenAIClient client = buildClient(model.url());

		ChatCompletionCreateParams params = ChatCompletionCreateParams.builder().addUserMessage(ctx.prompt().input())
				.model(model.id()).build();
		StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(params);

		FlowableOnSubscribe<Chunk> tokenObserver = emitter -> {
			logger.debug("Starting streaming response generation for {}", params);
			try {
				// cancel the upstream flow when the downstream cancels the flow
				emitter.setCancellable(() -> {
					logger.info(
							"The downstream subscriber cancelled the subscription. Closing OpenAI stream response.");
					streamResponse.close();
				});

				// iterate over the upstream stream and put chunks into the downstream
				// observable
				AtomicBoolean inThinkingArea = new AtomicBoolean(false);
				streamResponse.stream().filter(c -> !c.choices().isEmpty()).map(c -> c.choices().getFirst())
						.peek(choice -> {
							if (choice.finishReason().isPresent()) {
								logger.debug("LLM processing finishes with reason: {}", choice.finishReason());
							}
						}).filter(choice -> choice.delta().content().isPresent()).map(choice -> {
							String tokenStr = choice.delta().content().orElseThrow();
							boolean toggleArea = ReasoningUtils.isThinkingStartEndToken(tokenStr);
							boolean isThinking = toggleArea || inThinkingArea.get() == true;
							Chunk token = new ChunkImpl(tokenStr, isThinking);
							if (toggleArea) {
								logger.info("Toggling reasoning area flag");
								inThinkingArea.set(!inThinkingArea.get());
							}
							return token;
						}).forEach(emitter::onNext);

				// complete the downstream observable after reaching the end of the upstream
				// stream
				emitter.onComplete();
			} catch (Exception e) {
				logger.error("Caught an unexpected exception type while generating stream response: {}",
						e.getMessage());
				emitter.onError(new LLMException(
						"An error occurred while processing the LLM token stream: " + e.getMessage(), e));
			}
		};

		return Flowable.create(tokenObserver, BackpressureStrategy.BUFFER)
				.onBackpressureBuffer(STREAMING_BUFFER_SIZE, () -> {
					throw new LLMException("LLM Token Buffer overflow. Consumer was too slow.");
				}).subscribeOn(Schedulers.io());
	}

	@Override
	public LLMProviderType type() {
		return LLMProviderType.VLLM;
	}
	
	private OpenAIClient buildClient(String url) {
		OpenAIClient client = OpenAIOkHttpClient.builder()
				.baseUrl(url)
				.apiKey("bogus")
				.build();
		return client;
	}

}
