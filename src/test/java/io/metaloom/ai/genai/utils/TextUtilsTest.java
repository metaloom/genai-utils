package io.metaloom.ai.genai.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TextUtilsTest {

	@Test
	public void testCount() {
		assertEquals(0, TextUtils.count('?', null));
		assertEquals(0, TextUtils.count('?', ""));
		assertEquals(0, TextUtils.count('?', "ABCD"));
		assertEquals(1, TextUtils.count('?', "?"));
		assertEquals(2, TextUtils.count('?', "??"));
		assertEquals(3, TextUtils.count('?', "A?B?C?"));
	}

	@Test
	public void testIsNumber() {
		assertTrue(TextUtils.isNumber("0"));
		assertTrue(TextUtils.isNumber("0000"));
		assertTrue(TextUtils.isNumber("1000"));
		assertTrue(TextUtils.isNumber("1.42f"));
		assertTrue(TextUtils.isNumber("1.562"));
		assertTrue(TextUtils.isNumber("42"));
		assertTrue(TextUtils.isNumber("0.52"));

		assertFalse(TextUtils.isNumber("Hallo"));
		assertFalse(TextUtils.isNumber("Eins"));
		assertFalse(TextUtils.isNumber("eins1"));
		assertFalse(TextUtils.isNumber(""));
		assertFalse(TextUtils.isNumber(" "));
		assertFalse(TextUtils.isNumber(null));
	}

	@Test
	public void testSoftClamp() {
		assertEquals("ABCD", TextUtils.softClamp("ABCD", 1, '\n'));
		assertEquals("AB", TextUtils.softClamp("AB\nCD", 1, '\n'));
		assertEquals("AB\nCD\n", TextUtils.softClamp("AB\nCD\n", 100, '\n'));
		assertEquals("AB\nCD", TextUtils.softClamp("AB\nCD\n", 4, '\n'));
		assertEquals("AB.CD", TextUtils.softClamp("AB.CD\n", 4, '\n', '.'));
	}

	@Test
	public void trimsNonAsciiAtEnd() {
		assertEquals("Hello", TextUtils.trimNonAsciiFromEnd("Hello世界"));
		assertEquals("Data", TextUtils.trimNonAsciiFromEnd("Data✓"));
		assertEquals("Test", TextUtils.trimNonAsciiFromEnd("Testé"));
	}
}
