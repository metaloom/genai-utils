package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.openai.OpenAILLMProvider;
import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;

/**
 * Drives a reasoning model served by llama.cpp through the supported {@link ReasoningLevel}s.
 * Requires a running server - see {@link LLamaCPPTest}.
 */
public class LLamaCPPReasoningTest {

	private static final String URL = "http://localhost:8888/v1";

	private static final String QUESTION = "How many times does the letter r occur in the word strawberry? Answer with the number only.";

	private final LLMProvider provider = new OpenAILLMProvider();

	@Test
	public void testReasoningLevels() {
		Map<ReasoningLevel, Integer> reasoningSize = new LinkedHashMap<>();

		// The Qwen3.8 chat template only accepts low, medium and xhigh - a level the model does not
		// know fails the request.
		for (ReasoningLevel level : new ReasoningLevel[] { ReasoningLevel.OFF, ReasoningLevel.LOW, ReasoningLevel.MEDIUM,
			ReasoningLevel.XHIGH }) {
			StringBuilder reasoning = new StringBuilder();
			StringBuilder answer = new StringBuilder();

			provider.generateStream(ctx(level)).blockingSubscribe(chunk -> {
				(chunk.isThinking() ? reasoning : answer).append(chunk.toString());
			});

			System.out.println("--- " + level + ": " + reasoning.length() + " reasoning chars, answer: " + answer.toString().trim());
			reasoningSize.put(level, reasoning.length());
		}

		assertEquals(0, reasoningSize.get(ReasoningLevel.OFF), "OFF must not produce a reasoning trace");
		assertTrue(reasoningSize.get(ReasoningLevel.LOW) > 0, "LOW must still reason");
		assertTrue(reasoningSize.get(ReasoningLevel.XHIGH) > reasoningSize.get(ReasoningLevel.LOW),
			"XHIGH must reason more than LOW but was " + reasoningSize);
	}

	@Test
	public void testThinkingOff() {
		LLMResponse response = provider.generateResponse(ctx(ReasoningLevel.OFF));
		System.out.println(response.content());
		assertFalse(response.content().contains("<think>"), "The answer must not carry a reasoning trace");
	}

	private LLMContext ctx(ReasoningLevel level) {
		Prompt prompt = new PromptImpl(QUESTION);
		return LLMContext.ctx(prompt, TestModel.qwen3(URL)).reasoning(level);
	}
}
