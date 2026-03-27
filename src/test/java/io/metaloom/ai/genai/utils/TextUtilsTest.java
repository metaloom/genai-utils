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
	public void testTrimsNonAsciiAtEnd() {
		assertEquals("Hello", TextUtils.trimNonAsciiFromEnd("Hello世界"));
		assertEquals("Data", TextUtils.trimNonAsciiFromEnd("Data✓"));
		assertEquals("Test", TextUtils.trimNonAsciiFromEnd("Testé"));
	}

	@Test
	public void testRandomBase64Str() {
		String s = TextUtils.randomBase64Str(64);
		assertEquals(64, s.length());
		System.out.println(s);
	}
	
	@Test
	public void testTrimToWords() {
		assertEquals("Hallo Welt", TextUtils.trimToWords("Hallo Welt Johannes",2));
		assertEquals("Hallo Welt", TextUtils.trimToWords("Hallo Welt",2));
		assertEquals("Hallo Welt", TextUtils.trimToWords("Hallo Welt Johannes 1234",2));
		assertEquals("Hallo", TextUtils.trimToWords("Hallo",2));
		assertEquals("Hallo", TextUtils.trimToWords("Hallo ",2));
		assertEquals("Hallo", TextUtils.trimToWords("Hallo   ",2));
		assertEquals("Hallo  Welt", TextUtils.trimToWords("Hallo  Welt",2));
	}
	
	@Test
	public void testIsEnglish() {
		assertTrue(TextUtils.isEnglish("The brown dog jumped over the black fence."));
		assertFalse(TextUtils.isEnglish("Der braune Hund springt über den schwarzen Zaun."));
	}
}
