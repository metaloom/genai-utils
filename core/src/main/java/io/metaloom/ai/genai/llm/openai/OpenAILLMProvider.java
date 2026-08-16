package io.metaloom.ai.genai.llm.openai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletion.Choice;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionCreateParams.Builder;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;

import io.metaloom.ai.genai.llm.ChatMessage;
import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LLMResponse;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.StreamEvent;
import io.metaloom.ai.genai.llm.TokenUsage;
import io.metaloom.ai.genai.llm.ToolCall;
import io.metaloom.ai.genai.llm.ToolCallResponse;
import io.metaloom.ai.genai.llm.ToolDefinition;
import io.metaloom.ai.genai.llm.error.LLMException;
import io.metaloom.ai.genai.llm.impl.ChunkImpl;
import io.metaloom.ai.genai.utils.ReasoningUtils;
import io.metaloom.ai.genai.utils.TextUtils;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.json.JsonObject;

/**
 * The one LLM provider: it speaks the OpenAI chat-completions protocol and therefore drives any
 * server exposing it — llama.cpp, vLLM, Ollama's {@code /v1} endpoint, OpenAI itself.
 */
public class OpenAILLMProvider implements LLMProvider {

	private static final Logger logger = LoggerFactory.getLogger(OpenAILLMProvider.class);

	/**
	 * Buffer size for stream response (measured in number of tokens) for the case where stream consumer is slower than the producer. If the backpressure is
	 * bigger than that,
	 */
	private static final int STREAMING_BUFFER_SIZE = 8192;

	@Override
	public String generate(LLMContext ctx) {
		return generateResponse(ctx).content();
	}

	@Override
	public LLMResponse generateResponse(LLMContext ctx) {
		LargeLanguageModel model = ctx.model();
		OpenAIClient client = buildClient(model.url());

		Builder builder = ChatCompletionCreateParams.builder()
			.messages(convertHistory(ctx.chatHistory()))
			.temperature(ctx.temperature())
			.model(model.id());
		applyThinking(builder, ctx);

		ChatCompletionCreateParams params = builder.build();
		ChatCompletion chatCompletion = client.chat().completions().create(params);

		Choice firstChoice = chatCompletion.choices().getFirst();
		// llama.cpp (started with --reasoning auto) and other OpenAI-compatible servers
		// expose the reasoning trace in a separate, non-standard "reasoning_content"
		// field on the message. Surface it when thinking is enabled so the caller can
		// observe it. The OpenAI Java SDK exposes unknown fields via _additionalProperties().
		if (ctx.isThinkEnabled()) {
			String reasoning = extractReasoningContent(firstChoice.message()._additionalProperties());
			if (reasoning != null && !reasoning.isEmpty()) {
				logger.info("Reasoning content:\n{}", reasoning);
			}
		}
		return new LLMResponse(firstChoice.message().content().orElseThrow(), extractUsage(chatCompletion.usage()));
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

		Builder builder = ChatCompletionCreateParams.builder().addUserMessage(ctx.prompt().input())
			.model(model.id());
		applyThinking(builder, ctx);
		applyStreamUsage(builder, ctx);

		ChatCompletionCreateParams params = builder.build();
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
				// The token accounting arrives in a trailing, choice-less chunk (only when
				// stream_options.include_usage was requested), so capture it before the
				// choice filter drops that chunk.
				AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.NONE);
				streamResponse.stream()
					.peek(c -> captureUsage(c, usage))
					.filter(c -> !c.choices().isEmpty()).map(c -> c.choices().getFirst())
					.peek(choice -> {
						if (choice.finishReason().isPresent()) {
							logger.debug("LLM processing finishes with reason: {}", choice.finishReason());
						}
					})
					.peek(choice -> {
						// Surface reasoning_content emitted as a separate field by
						// llama.cpp (--reasoning auto) and similar OpenAI-compatible
						// servers that pre-split the thinking trace out of `content`.
						String reasoning = extractReasoningContent(choice.delta()._additionalProperties());
						if (reasoning != null && !reasoning.isEmpty()) {
							emitter.onNext(new ChunkImpl(reasoning, true));
						}
					})
					.filter(choice -> choice.delta().content().isPresent()).map(choice -> {
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

				// Close the stream with one text-less chunk that carries the token accounting.
				if (usage.get().isReported()) {
					emitter.onNext(ChunkImpl.usageChunk(usage.get()));
				}

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
	public Flowable<StreamEvent> generateStreamWithTools(LLMContext ctx) {
		LargeLanguageModel model = ctx.model();
		OpenAIClient client = buildClient(model.url());

		Builder paramsBuilder = ChatCompletionCreateParams.builder()
			.messages(convertHistory(ctx.chatHistory()))
			.temperature(ctx.temperature())
			.model(model.id());
		applyThinking(paramsBuilder, ctx);
		applyStreamUsage(paramsBuilder, ctx);
		addTools(paramsBuilder, ctx.tools());

		ChatCompletionCreateParams params = paramsBuilder.build();
		StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(params);

		FlowableOnSubscribe<StreamEvent> eventObserver = emitter -> {
			logger.debug("Starting streaming tool-call generation for {}", params);
			try {
				emitter.setCancellable(() -> {
					logger.info("The downstream subscriber cancelled the subscription. Closing OpenAI stream response.");
					streamResponse.close();
				});

				StringBuilder fullText = new StringBuilder();
				ToolCallAccumulator toolCalls = new ToolCallAccumulator();
				AtomicBoolean inThinkingArea = new AtomicBoolean(false);
				// The token accounting arrives in a trailing, choice-less chunk (only when
				// stream_options.include_usage was requested), so capture it before the
				// choice filter drops that chunk.
				AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.NONE);

				streamResponse.stream()
					.peek(c -> captureUsage(c, usage))
					.filter(c -> !c.choices().isEmpty()).map(c -> c.choices().getFirst())
					.forEach(choice -> {
						if (choice.finishReason().isPresent()) {
							logger.debug("LLM processing finishes with reason: {}", choice.finishReason());
						}

						// Servers that pre-split the thinking trace carry it in the
						// non-standard "reasoning_content" delta field.
						String reasoning = extractReasoningContent(choice.delta()._additionalProperties());
						if (reasoning != null && !reasoning.isEmpty()) {
							emitter.onNext(new StreamEvent.ReasoningDelta(reasoning));
						}

						choice.delta().content().ifPresent(text -> {
							// Models that inline their reasoning delimit it with <think>
							// markers inside the regular content stream instead.
							boolean toggleArea = ReasoningUtils.isThinkingStartEndToken(text);
							boolean isThinking = toggleArea || inThinkingArea.get();
							if (toggleArea) {
								inThinkingArea.set(!inThinkingArea.get());
							}
							if (isThinking) {
								emitter.onNext(new StreamEvent.ReasoningDelta(text));
							} else {
								fullText.append(text);
								emitter.onNext(new StreamEvent.TextDelta(text));
							}
						});

						choice.delta().toolCalls().ifPresent(toolCalls::accept);
					});

				List<ToolCall> calls = toolCalls.toToolCalls();
				if (!calls.isEmpty()) {
					emitter.onNext(new StreamEvent.ToolCallsComplete(calls));
				}
				emitter.onNext(new StreamEvent.Completed(fullText.isEmpty() ? null : fullText.toString(), usage.get()));
				emitter.onComplete();
			} catch (Exception e) {
				logger.error("Caught an unexpected exception type while generating streaming tool calls: {}",
					e.getMessage());
				emitter.onError(new LLMException(
					"An error occurred while processing the LLM token stream: " + e.getMessage(), e));
			}
		};

		return Flowable.create(eventObserver, BackpressureStrategy.BUFFER)
			.onBackpressureBuffer(STREAMING_BUFFER_SIZE, () -> {
				throw new LLMException("LLM Token Buffer overflow. Consumer was too slow.");
			}).subscribeOn(Schedulers.io());
	}

	@Override
	public ToolCallResponse generateWithTools(LLMContext ctx) {
		LargeLanguageModel model = ctx.model();
		OpenAIClient client = buildClient(model.url());

		Builder paramsBuilder = ChatCompletionCreateParams.builder()
			.addUserMessage(ctx.prompt().input())
			.temperature(ctx.temperature())
			.model(model.id());

		addTools(paramsBuilder, ctx.tools());

		ChatCompletionCreateParams params = paramsBuilder.build();
		ChatCompletion chatCompletion = client.chat().completions().create(params);

		Choice firstChoice = chatCompletion.choices().getFirst();
		String content = firstChoice.message().content().orElse(null);

		List<ToolCall> toolCalls = Collections.emptyList();
		if (firstChoice.message().toolCalls().isPresent()) {
			toolCalls = firstChoice.message().toolCalls().get().stream()
				.filter(ChatCompletionMessageToolCall::isFunction)
				.map(tc -> {
					ChatCompletionMessageFunctionToolCall ftc = tc.asFunction();
					return new ToolCall(
						ftc.id(),
						ftc.function().name(),
						new JsonObject(ftc.function().arguments()));
				})
				.collect(Collectors.toList());
		}
		return new ToolCallResponse(content, toolCalls, extractUsage(chatCompletion.usage()));
	}

	/**
	 * Collects the {@code tool_calls} fragments of a streamed response. A server spreads one tool
	 * call over several chunks: the first carries the id and the function name, the ones after it
	 * carry successive slices of the argument JSON. Fragments are keyed by the {@code index} the
	 * server assigns, which is what keeps parallel tool calls apart.
	 */
	static final class ToolCallAccumulator {

		private final Map<Long, Fragment> fragments = new LinkedHashMap<>();

		private static final class Fragment {
			private String id;
			private String name;
			private final StringBuilder arguments = new StringBuilder();
		}

		void accept(List<ChatCompletionChunk.Choice.Delta.ToolCall> deltas) {
			for (ChatCompletionChunk.Choice.Delta.ToolCall delta : deltas) {
				Fragment fragment = fragments.computeIfAbsent(delta.index(), k -> new Fragment());
				delta.id().ifPresent(id -> fragment.id = id);
				delta.function().ifPresent(function -> {
					function.name().ifPresent(name -> fragment.name = name);
					function.arguments().ifPresent(fragment.arguments::append);
				});
			}
		}

		List<ToolCall> toToolCalls() {
			List<ToolCall> calls = new ArrayList<>();
			for (Fragment fragment : fragments.values()) {
				if (fragment.name == null) {
					// Without a function name there is nothing to dispatch to.
					logger.warn("Discarding a streamed tool call without a function name");
					continue;
				}
				String args = fragment.arguments.isEmpty() ? "{}" : fragment.arguments.toString();
				calls.add(new ToolCall(fragment.id, fragment.name, new JsonObject(args)));
			}
			return calls;
		}
	}

	private void addTools(Builder builder, List<ToolDefinition> tools) {
		if (tools == null) {
			return;
		}
		for (ToolDefinition tool : tools) {
			FunctionDefinition funcDef = FunctionDefinition.builder()
				.name(tool.name())
				.description(tool.description())
				.parameters(convertToFunctionParameters(tool.parameters()))
				.build();
			builder.addFunctionTool(funcDef);
		}
	}

	/**
	 * Ask the server to report the token accounting of a streamed call. A streamed OpenAI-compatible
	 * response carries no {@code usage} object unless {@code stream_options.include_usage} was
	 * requested; with the flag set, vLLM and friends append one final, choice-less chunk holding it.
	 */
	private void applyStreamUsage(Builder builder, LLMContext ctx) {
		if (ctx.isStreamUsageEnabled()) {
			builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
		}
	}

	/**
	 * Remember the token accounting of a streamed call once the server sends it.
	 */
	private static void captureUsage(ChatCompletionChunk chunk, AtomicReference<TokenUsage> target) {
		TokenUsage usage = extractUsage(chunk.usage());
		if (usage.isReported()) {
			target.set(usage);
		}
	}

	/**
	 * Convert the {@code usage} object of an OpenAI-compatible response into the API type. Servers
	 * differ in how much of it they fill in - a partial or absent object yields
	 * {@link TokenUsage#NONE} rather than an error, since token accounting is never essential to the
	 * result of the call.
	 */
	private static TokenUsage extractUsage(Optional<CompletionUsage> usage) {
		if (usage == null || usage.isEmpty()) {
			return TokenUsage.NONE;
		}
		try {
			CompletionUsage u = usage.get();
			long reasoningTokens = u.completionTokensDetails()
				.flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
				.orElse(0L);
			long cachedTokens = u.promptTokensDetails()
				.flatMap(CompletionUsage.PromptTokensDetails::cachedTokens)
				.orElse(0L);
			return new TokenUsage(u.promptTokens(), u.completionTokens(), u.totalTokens(), reasoningTokens, cachedTokens);
		} catch (Exception e) {
			logger.debug("Server reported an incomplete usage object. Ignoring the token accounting.", e);
			return TokenUsage.NONE;
		}
	}

	/**
	 * Ask the server to run the model in reasoning mode. {@code chat_template_kwargs} is how
	 * llama.cpp and vLLM pass the flag into the chat template; servers that do not know the field
	 * ignore it.
	 */
	private void applyThinking(Builder builder, LLMContext ctx) {
		if (ctx.isThinkEnabled()) {
			logger.debug("Enable thinking mode");
			builder.putAdditionalBodyProperty(
				"chat_template_kwargs",
				JsonValue.from(Map.of(
					"enable_thinking", true)));
		}
	}

	private List<ChatCompletionMessageParam> convertHistory(List<? extends ChatMessage> chatHistory) {
		return chatHistory.stream().map(entry -> {
			String role = entry.getRole().toLowerCase();
			String text = entry.getText();
			if (role.equalsIgnoreCase("user")) {
				return ChatCompletionMessageParam.ofUser(
					ChatCompletionUserMessageParam.builder().content(text).build());
			} else if (role.equalsIgnoreCase("system")) {
				return ChatCompletionMessageParam.ofSystem(
					ChatCompletionSystemMessageParam.builder().content(text).build());
			} else if (role.equalsIgnoreCase("tool")) {
				return ChatCompletionMessageParam.ofTool(
					ChatCompletionToolMessageParam.builder()
						.toolCallId(entry.getToolCallId())
						.content(text)
						.build());
			} else if (role.equalsIgnoreCase("assistant") && !entry.getToolCalls().isEmpty()) {
				ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder();
				if (text != null) {
					builder.content(text);
				}
				for (ToolCall tc : entry.getToolCalls()) {
					builder.addToolCall(ChatCompletionMessageFunctionToolCall.builder()
						.id(tc.id())
						.function(ChatCompletionMessageFunctionToolCall.Function.builder()
							.name(tc.name())
							.arguments(tc.arguments().encode())
							.build())
						.build());
				}
				return ChatCompletionMessageParam.ofAssistant(builder.build());
			} else {
				return ChatCompletionMessageParam.ofAssistant(
					ChatCompletionAssistantMessageParam.builder().content(text).build());
			}
		}).collect(Collectors.toList());
	}

	private FunctionParameters convertToFunctionParameters(JsonObject params) {
		if (params == null) {
			return FunctionParameters.builder().build();
		}
		FunctionParameters.Builder builder = FunctionParameters.builder();
		Map<String, Object> map = params.getMap();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			builder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
		}
		return builder.build();
	}

	private OpenAIClient buildClient(String url) {
		OpenAIClient client = OpenAIOkHttpClient.builder()
			.baseUrl(url)
			.apiKey("bogus")
			.build();
		return client;
	}

	/**
	 * Extract the value of the non-standard "reasoning_content" field that llama.cpp
	 * (when started with --reasoning auto) and other OpenAI-compatible servers add to
	 * the chat message / delta to expose the model's chain-of-thought separately
	 * from the regular response content.
	 *
	 * @param additionalProperties additional/unknown properties map provided by the
	 *                             OpenAI Java SDK on a message or streaming delta
	 * @return the reasoning text, or {@code null} when no such field is present
	 */
	private static String extractReasoningContent(Map<String, JsonValue> additionalProperties) {
		if (additionalProperties == null) {
			return null;
		}
		JsonValue value = additionalProperties.get("reasoning_content");
		if (value == null) {
			return null;
		}
		Object raw = value.asString().orElse(null);
		return raw instanceof String ? (String) raw : null;
	}

}
