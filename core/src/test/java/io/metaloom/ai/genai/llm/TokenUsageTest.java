package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TokenUsageTest {

	@Test
	public void testTotalIsDerivedWhenNotReported() {
		TokenUsage usage = TokenUsage.of(120, 30);

		assertEquals(120, usage.promptTokens());
		assertEquals(30, usage.completionTokens());
		assertEquals(150, usage.totalTokens(), "The total should have been derived from prompt + completion");
	}

	@Test
	public void testReportedTotalIsKept() {
		// Some servers count the total differently (e.g. cached prefixes) - never overwrite it.
		TokenUsage usage = new TokenUsage(120, 30, 200, 10, 100);

		assertEquals(200, usage.totalTokens());
		assertEquals(10, usage.reasoningTokens());
		assertEquals(100, usage.cachedPromptTokens());
	}

	@Test
	public void testIsReported() {
		assertFalse(TokenUsage.NONE.isReported());
		assertTrue(TokenUsage.of(1, 0).isReported());
		assertTrue(TokenUsage.of(0, 1).isReported());
	}

	@Test
	public void testPlus() {
		TokenUsage turn1 = new TokenUsage(100, 20, 0, 5, 10);
		TokenUsage turn2 = new TokenUsage(200, 40, 0, 7, 20);

		TokenUsage total = turn1.plus(turn2).plus(null);

		assertEquals(300, total.promptTokens());
		assertEquals(60, total.completionTokens());
		assertEquals(360, total.totalTokens());
		assertEquals(12, total.reasoningTokens());
		assertEquals(30, total.cachedPromptTokens());
	}
}
