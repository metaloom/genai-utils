package io.metaloom.ai.genai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.llm.prompt.Prompt;
import io.metaloom.ai.genai.llm.prompt.impl.PromptImpl;

public class ReasoningLevelTest {

	@AfterEach
	public void clearProperty() {
		System.clearProperty(ReasoningLevel.LEVEL_PROPERTY_KEY);
	}

	@Test
	public void testWireValues() {
		assertNull(ReasoningLevel.UNSET.thinking());
		assertNull(ReasoningLevel.UNSET.effort());
		assertFalse(ReasoningLevel.UNSET.isSet());

		assertEquals(Boolean.FALSE, ReasoningLevel.OFF.thinking());
		assertEquals("none", ReasoningLevel.OFF.effort());
		assertFalse(ReasoningLevel.OFF.isThinkEnabled());

		assertEquals(Boolean.TRUE, ReasoningLevel.ON.thinking());
		assertNull(ReasoningLevel.ON.effort(), "ON must not state an effort");

		assertEquals("low", ReasoningLevel.LOW.effort());
		assertEquals("medium", ReasoningLevel.MEDIUM.effort());
		assertEquals("high", ReasoningLevel.HIGH.effort());
		assertEquals("xhigh", ReasoningLevel.XHIGH.effort());
		assertEquals("minimal", ReasoningLevel.MINIMAL.effort());

		for (ReasoningLevel level : ReasoningLevel.values()) {
			boolean thinking = level != ReasoningLevel.UNSET && level != ReasoningLevel.OFF;
			assertEquals(thinking, level.isThinkEnabled(), "Wrong think flag for " + level);
		}
	}

	@Test
	public void testParse() {
		assertEquals(ReasoningLevel.UNSET, ReasoningLevel.parse(null));
		assertEquals(ReasoningLevel.UNSET, ReasoningLevel.parse("  "));
		assertEquals(ReasoningLevel.XHIGH, ReasoningLevel.parse("xhigh"));
		assertEquals(ReasoningLevel.XHIGH, ReasoningLevel.parse(" XHigh "));
		assertEquals(ReasoningLevel.OFF, ReasoningLevel.parse("none"));
		assertEquals(ReasoningLevel.OFF, ReasoningLevel.parse("off"));
		assertEquals(ReasoningLevel.OFF, ReasoningLevel.parse("false"));
		assertEquals(ReasoningLevel.ON, ReasoningLevel.parse("on"));
		assertEquals(ReasoningLevel.ON, ReasoningLevel.parse("true"));
		assertThrows(IllegalArgumentException.class, () -> ReasoningLevel.parse("ludicrous"));
		assertEquals(ReasoningLevel.MEDIUM, ReasoningLevel.parse("ludicrous", ReasoningLevel.MEDIUM));
	}

	@Test
	public void testContextDefaultIsUnset() {
		LLMContext ctx = ctx();
		assertEquals(ReasoningLevel.UNSET, ctx.reasoningLevel());
		assertFalse(ctx.isThinkEnabled());
	}

	@Test
	public void testEnableThinkMapsToOn() {
		LLMContext ctx = ctx().enableThink();
		assertEquals(ReasoningLevel.ON, ctx.reasoningLevel());
		assertTrue(ctx.isThinkEnabled());
	}

	@Test
	public void testSetLevel() {
		LLMContext ctx = ctx().reasoning(ReasoningLevel.LOW);
		assertEquals(ReasoningLevel.LOW, ctx.reasoningLevel());
		assertTrue(ctx.isThinkEnabled());

		ctx.setReasoningLevel(ReasoningLevel.OFF);
		assertEquals(ReasoningLevel.OFF, ctx.reasoningLevel());
		assertFalse(ctx.isThinkEnabled());

		ctx.setReasoningLevel(null);
		assertEquals(ReasoningLevel.UNSET, ctx.reasoningLevel(), "null must reset the level");
	}

	@Test
	public void testSystemPropertyDefault() {
		System.setProperty(ReasoningLevel.LEVEL_PROPERTY_KEY, "medium");
		assertEquals(ReasoningLevel.MEDIUM, ReasoningLevel.defaultLevel());
		assertEquals(ReasoningLevel.MEDIUM, ctx().reasoningLevel(), "A new context must pick up the configured default");

		System.setProperty(ReasoningLevel.LEVEL_PROPERTY_KEY, "bogus");
		assertEquals(ReasoningLevel.UNSET, ReasoningLevel.defaultLevel(), "An unparsable value must not fail the call");
	}

	private LLMContext ctx() {
		Prompt prompt = new PromptImpl("Write hello world in plain text");
		return LLMContext.ctx(prompt, new TestModel("test-model", "http://localhost:1/v1", 4096));
	}
}
