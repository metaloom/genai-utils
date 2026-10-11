package io.metaloom.ai.genai.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import io.metaloom.ai.genai.llm.ChatMessage;
import io.metaloom.ai.genai.llm.LLMContext;
import io.metaloom.ai.genai.llm.LargeLanguageModel;
import io.metaloom.ai.genai.llm.TestModel;
import io.metaloom.ai.genai.llm.ToolCall;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;
import io.vertx.core.json.JsonObject;

/**
 * A tool-calling request must carry the conversation, not just the newest user message.
 *
 * <p>
 * {@code generateWithTools} sent only {@code prompt().input()} while the streaming path sent the
 * whole history. An agent looping through the blocking path therefore re-sent one bare user
 * message every turn — without the system prompt, and without the assistant {@code tool_calls}
 * and the {@code tool} message answering them. The model had no way to know it had already called
 * the tool, so it called the same one again on every turn until the loop hit its turn cap.
 * </p>
 *
 * <p>
 * The symptom is indistinguishable from a weak model looping, which is what makes it worth a test
 * rather than a comment: on a real deployment it cost eight turns and a {@code TURN_LIMIT} error
 * for a question one tool call answers.
 * </p>
 */
public class ToolLoopHistoryTest {

	private static final LargeLanguageModel MODEL = TestModel.qwen3("http://127.0.0.1:8080/v1");

	private static LLMContext ctxWith(List<ChatMessage> history, String prompt) {
		return LLMContext.ctx(history, MODEL, new PromptImpl(prompt));
	}

	@Test
	@DisplayName("The whole conversation is sent, tool call and tool result included")
	public void testHistoryIsSent() {
		ToolCall call = new ToolCall("call_1", "asset_statistics", new JsonObject());
		List<ChatMessage> history = List.of(
			ChatMessage.system("You are Loom."),
			ChatMessage.user("How many assets are there?"),
			ChatMessage.assistantWithToolCalls(List.of(call)),
			ChatMessage.toolResult("call_1", "asset_statistics", "{\"totalAssets\":23}"));

		List<ChatCompletionMessageParam> sent = new OpenAILLMProvider().messagesFor(ctxWith(history, "How many assets are there?"));

		assertEquals(4, sent.size(), "every message of the conversation goes out");
		assertTrue(sent.get(0).isSystem(), "the system prompt must reach the model");
		assertTrue(sent.get(1).isUser());
		// The pair that ends the loop: without both, the model re-issues the same call forever.
		assertTrue(sent.get(2).isAssistant(), "the assistant's tool_calls must be replayed");
		assertTrue(sent.get(3).isTool(), "the tool result must be replayed");
		assertEquals("call_1", sent.get(3).asTool().toolCallId());
	}

	@Test
	@DisplayName("With no history the prompt is still sent, so older callers keep working")
	public void testPromptFallback() {
		// Some callers fill in a one-shot prompt and no history. An empty message list is not a
		// request any server accepts, so the prompt has to stand in.
		List<ChatCompletionMessageParam> sent = new OpenAILLMProvider().messagesFor(ctxWith(List.of(), "Summarise this."));

		assertEquals(1, sent.size());
		assertTrue(sent.get(0).isUser());
	}
}
