package io.metaloom.ai.genai.llm.ollama;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel.OllamaStreamingChatModelBuilder;
import io.metaloom.ai.genai.llm.ChatMessage;
import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LLMProvider;
import io.metaloom.ai.genai.llm.LLMProviderType;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.impl.ChunkImpl;
import io.metaloom.ai.genai.utils.TextUtils;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;

public class OllamaLLMProvider implements LLMProvider {

	private static final Logger logger = LoggerFactory.getLogger(OllamaLLMProvider.class);

	@Override
	public String generate(LLMContext ctx) {
		return generate(ctx, "text");
	}

	public String generate(LLMContext ctx, String format) {
		LargeLanguageModel llm = ctx.model();
		String url = llm.url();
		logger.debug("Using server {} for model {}", url, llm);
		OllamaChatModelBuilder builder = OllamaChatModel.builder()
			.baseUrl(url)
			// .topP(null)
			// .topK(null)
			// .repeatPenalty(10d)
			.timeout(Duration.ofMinutes(15))
			.modelName(ctx.model().id())
			.numPredict(ctx.tokenOutputLimit())
			.temperature(ctx.temperature());

		if (ctx.seed() != null) {
			builder.seed(ctx.seed());
		}
		if (format != null && !format.equalsIgnoreCase("text")) {
			builder.format(format);
		}
		ChatLanguageModel model = builder.build();

		List<dev.langchain4j.data.message.ChatMessage> msgs = convertHistory(ctx.chatHistory());
		ChatResponse response = model.chat(msgs);
		return response.aiMessage().text();
	}

	@Override
	public JsonObject generateJson(LLMContext ctx) {
		String jsonStr = generate(ctx, "json");
		try {
			jsonStr = TextUtils.extractJson(jsonStr);
			return new JsonObject(jsonStr);
		} catch (Exception e) {
			logger.error("Failed to parse JSON", e);
			logger.error("JSON from LLM:\n{}", jsonStr);
			throw new RuntimeException("Failed to parse JSON", e);
		}
	}

	@Override
	public Flowable<Chunk> generateStream(LLMContext ctx) {
		LargeLanguageModel llm = ctx.model();
		String url = llm.url();
		logger.info("Using server {} for model {}", url, llm);
		OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
			.baseUrl(url)
			.timeout(Duration.ofMinutes(15))
			.modelName(ctx.model().id())
			.numPredict(ctx.tokenOutputLimit())
			.temperature(ctx.temperature());

		if (ctx.seed() != null) {
			builder.seed(ctx.seed());
		}

		OllamaStreamingChatModel model = builder.build();

		List<dev.langchain4j.data.message.ChatMessage> msgs = convertHistory(ctx.chatHistory());
		return Flowable.create(sub -> {
			ChatRequest request = ChatRequest.builder().messages(msgs).build();
			model.doChat(request, new StreamingChatResponseHandler() {

				@Override
				public void onError(Throwable error) {
					logger.error("Error while processing ollama request", error);
					sub.onError(error);
				}

				@Override
				public void onPartialResponse(String partialResponse) {
					sub.onNext(new ChunkImpl(partialResponse));

				}

				@Override
				public void onCompleteResponse(ChatResponse completeResponse) {
					logger.info("Ollama call completed with msg.");
					sub.onComplete();
				}
			});
		}, BackpressureStrategy.BUFFER);
	}

	private List<dev.langchain4j.data.message.ChatMessage> convertHistory(List<? extends ChatMessage> chatHistory) {
		return chatHistory.stream().map(entry -> {
			String role = entry.getRole().toLowerCase();
			String text = entry.getText();
			if (role.equalsIgnoreCase("control")) {
				return new ControlMessage(text);
			} else if (role.equalsIgnoreCase("user")) {
				return new UserMessage(text);
			} else {
				return new AiMessage(text);
			}
		}).collect(Collectors.toList());
	}

	public void listModels(String url) {
		logger.info("Listing models endpoint {}", url);
		OllamaModels models = OllamaModels.builder().baseUrl(url).build();
		for (OllamaModel model : models.availableModels().content()) {
			System.out.println("Model: " + model.getName());
		}
	}

	@Override
	public LLMProviderType type() {
		return LLMProviderType.OLLAMA;
	}
}
