package io.metaloom.ai.genai.llm.prompt.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PromptImplTest {

	@Test
	public void testParametersAreSubstituted() {
		PromptImpl prompt = new PromptImpl("Translate from ${source} to ${target}: ${text}");
		prompt.parameters().put("source", "en");
		prompt.parameters().put("target", "de");
		prompt.parameters().put("text", "hello");
		assertEquals("Translate from en to de: hello", prompt.input());
	}

	@Test
	public void testPlaceholderInsideAValueIsNotSubstituted() {
		// Untrusted content carrying a placeholder must come out verbatim, whatever order the map iterates in.
		// Many keys make it near certain that some key is iterated after "text" in a HashMap.
		StringBuilder template = new StringBuilder("${text}");
		StringBuilder value = new StringBuilder();
		PromptImpl probe = new PromptImpl(null);
		for (int i = 0; i < 50; i++) {
			template.append(" ${k").append(i).append('}');
			value.append("${k").append(i).append('}');
			probe.parameters().put("k" + i, "X");
		}
		PromptImpl prompt = new PromptImpl(template.toString());
		prompt.parameters().putAll(probe.parameters());
		prompt.parameters().put("text", value.toString());
		assertEquals(value + " X".repeat(50), prompt.input());
	}

	@Test
	public void testValueIsNotReplacedTwice() {
		PromptImpl prompt = new PromptImpl("${a}|${b}");
		prompt.parameters().put("a", "${b}");
		prompt.parameters().put("b", "B");
		assertEquals("${b}|B", prompt.input());
	}

	@Test
	public void testUnknownPlaceholderIsKept() {
		PromptImpl prompt = new PromptImpl("${known} ${unknown} ${");
		prompt.parameters().put("known", "K");
		assertEquals("K ${unknown} ${", prompt.input());
	}

	@Test
	public void testTemplateWithoutParameters() {
		PromptImpl prompt = new PromptImpl("plain ${x}");
		prompt.setText(" tail");
		assertEquals("plain ${x} tail", prompt.input());
	}

	@Test
	public void testRepeatedPlaceholder() {
		PromptImpl prompt = new PromptImpl("${a}-${a}");
		prompt.parameters().put("a", "1");
		assertEquals("1-1", prompt.input());
	}
}
