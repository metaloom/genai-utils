package io.metaloom.ai.genai.llm.ollama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
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
import io.metaloom.ai.genai.llm.ToolCall;
import io.metaloom.ai.genai.llm.ToolCallResponse;
import io.metaloom.ai.genai.llm.ToolDefinition;
import io.metaloom.ai.genai.llm.impl.ChunkImpl;
import io.metaloom.ai.genai.utils.TextUtils;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.json.JsonArray;
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
			.maxRetries(1)
			.timeout(Duration.ofSeconds(60))
			.modelName(ctx.model().id())
			.numCtx(16384)
			//.numPredict(ctx.tokenOutputLimit())
			.temperature(ctx.temperature());

		if (ctx.isThinkEnabled()) {
			builder.think(true);
		}

		if (ctx.seed() != null) {
			builder.seed(ctx.seed());
		}
		if (format != null && !format.equalsIgnoreCase("text")) {
			builder.responseFormat(ResponseFormat.JSON);
		}
		OllamaChatModel model = builder.build();

		List<dev.langchain4j.data.message.ChatMessage> msgs = convertHistory(ctx.chatHistory());
		ChatResponse response = model.chat(msgs);
		if (response.aiMessage().thinking() != null) {
			System.err.println(response.aiMessage().thinking());
		}
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
			.think(ctx.isThinkEnabled())
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
					sub.onNext(new ChunkImpl(partialResponse, false));
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
				return (dev.langchain4j.data.message.ChatMessage) new ControlMessage();
			} else if (role.equalsIgnoreCase("user")) {
				return (dev.langchain4j.data.message.ChatMessage) new UserMessage(text);
			} else if (role.equalsIgnoreCase("system")) {
				return (dev.langchain4j.data.message.ChatMessage) new SystemMessage(text);
			} else if (role.equalsIgnoreCase("tool")) {
				return (dev.langchain4j.data.message.ChatMessage) new ToolExecutionResultMessage(
					entry.getToolCallId(), entry.getToolName(), text);
			} else if (role.equalsIgnoreCase("assistant") && !entry.getToolCalls().isEmpty()) {
				// Assistant message that requested tool calls
				List<ToolExecutionRequest> requests = entry.getToolCalls().stream()
					.map(tc -> ToolExecutionRequest.builder()
						.id(tc.id())
						.name(tc.name())
						.arguments(tc.arguments().encode())
						.build())
					.collect(Collectors.toList());
				return (dev.langchain4j.data.message.ChatMessage) new AiMessage(text, requests);
			} else {
				return (dev.langchain4j.data.message.ChatMessage) new AiMessage(text);
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
	public ToolCallResponse generateWithTools(LLMContext ctx) {
		LargeLanguageModel llm = ctx.model();
		String url = llm.url();
		logger.debug("Using server {} for model {} (tool calling)", url, llm);

		OllamaChatModelBuilder builder = OllamaChatModel.builder()
			.baseUrl(url)
			.maxRetries(1)
			.timeout(Duration.ofSeconds(60))
			.modelName(ctx.model().id())
			.numCtx(16384)
			.temperature(ctx.temperature());

		if (ctx.seed() != null) {
			builder.seed(ctx.seed());
		}

		OllamaChatModel model = builder.build();

		List<dev.langchain4j.data.message.ChatMessage> msgs = convertHistory(ctx.chatHistory());
		List<ToolSpecification> toolSpecs = convertToolDefinitions(ctx.tools());

		ChatRequest request = ChatRequest.builder()
			.messages(msgs)
			.toolSpecifications(toolSpecs)
			.build();

		ChatResponse response = model.chat(request);
		AiMessage aiMessage = response.aiMessage();

		String content = aiMessage.text();
		List<ToolCall> toolCalls = Collections.emptyList();
		if (aiMessage.hasToolExecutionRequests()) {
			toolCalls = aiMessage.toolExecutionRequests().stream()
				.map(req -> new ToolCall(
					req.id(),
					req.name(),
					new JsonObject(req.arguments())))
				.collect(Collectors.toList());
		}
		return new ToolCallResponse(content, toolCalls);
	}

	private List<ToolSpecification> convertToolDefinitions(List<ToolDefinition> tools) {
		if (tools == null || tools.isEmpty()) {
			return Collections.emptyList();
		}
		return tools.stream().map(tool -> {
			ToolSpecification.Builder builder = ToolSpecification.builder()
				.name(tool.name())
				.description(tool.description());

			if (tool.parameters() != null) {
				builder.parameters(convertToJsonObjectSchema(tool.parameters()));
			}
			return builder.build();
		}).collect(Collectors.toList());
	}

	private JsonObjectSchema convertToJsonObjectSchema(JsonObject params) {
		JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
		JsonObject properties = params.getJsonObject("properties");
		if (properties != null) {
			Map<String, JsonSchemaElement> propMap = new HashMap<>();
			for (String key : properties.fieldNames()) {
				JsonObject prop = properties.getJsonObject(key);
				propMap.put(key, convertPropertyToSchemaElement(prop));
			}
			schemaBuilder.addProperties(propMap);
		}
		JsonArray required = params.getJsonArray("required");
		if (required != null) {
			List<String> reqList = new ArrayList<>();
			for (int i = 0; i < required.size(); i++) {
				reqList.add(required.getString(i));
			}
			schemaBuilder.required(reqList);
		}
		return schemaBuilder.build();
	}

	private JsonSchemaElement convertPropertyToSchemaElement(JsonObject prop) {
		String type = prop.getString("type", "string");
		String description = prop.getString("description");
		return switch (type) {
			case "integer" -> JsonIntegerSchema.builder().description(description).build();
			case "number" -> JsonNumberSchema.builder().description(description).build();
			case "boolean" -> JsonBooleanSchema.builder().description(description).build();
			case "array" -> {
				JsonArraySchema.Builder arrBuilder = JsonArraySchema.builder().description(description);
				JsonObject items = prop.getJsonObject("items");
				if (items != null) {
					arrBuilder.items(convertPropertyToSchemaElement(items));
				}
				yield arrBuilder.build();
			}
			case "object" -> {
				if (prop.containsKey("properties")) {
					yield convertToJsonObjectSchema(prop);
				}
				yield JsonObjectSchema.builder().description(description).build();
			}
			default -> JsonStringSchema.builder().description(description).build();
		};
	}

	@Override
	public LLMProviderType type() {
		return LLMProviderType.OLLAMA;
	}
}
