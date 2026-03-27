package io.metaloom.ai.genai.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

public final class TextUtils {

	private static final String UMLAUTS = "\\u00c4\\u00e4\\u00d6\\u00f6\\u00dc\\u00fc\\u00df\\p{L}";

	private static final String OTHER_REGEX = "(?<other>.)";

	private static final String WORD_REGEX = "(?<word>[" + UMLAUTS + "\\w]+)";

	public static final SecureRandom RANDOM = new SecureRandom();

	public TextUtils() {
	}

	public static String quote(String text) {
		return text.replaceAll(".*\\R|.+\\z", Matcher.quoteReplacement("> ") + "$0");
	}

	public static int count(char pattern, String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}

		int count = 0;
		for (char c : text.toCharArray()) {
			if (c == pattern) {
				count++;
			}
		}
		return count;
	}

	public static String extractJson(String text) {
		if (text == null) {
			return null;
		}
		List<String> outputLines = new ArrayList<>();
		try (Scanner scanner = new Scanner(text)) {
			boolean foundStart = false;
			while (scanner.hasNext()) {
				String line = scanner.nextLine();
				if (line.trim().startsWith("{") || line.trim().startsWith("[")) {
					foundStart = true;
				}
				if (foundStart) {
					outputLines.add(line + "\n");
				}
			}
		}

		for (int i = outputLines.size() - 1; i >= 0; i--) {
			String line = outputLines.get(i);
			if (line.trim().endsWith("}") || line.trim().endsWith("]")) {
				break;
			} else {
				outputLines.remove(i);
			}
		}

		StringBuilder builder = new StringBuilder();
		for (String line : outputLines) {
			builder.append(line);
		}
		return builder.toString();
	}

	public static String softClamp(String text, int maxLen, char... stopChars) {
		if (text == null) {
			return null;
		}
		if (text.length() <= maxLen) {
			return text;
		}

		Set<Character> stopCharSet = new HashSet<>();
		for (char c : stopChars) {
			stopCharSet.add(c);
		}

		StringBuilder b = new StringBuilder();
		int count = 0;
		for (char c : text.toCharArray()) {
			boolean isStopChar = stopCharSet.contains(c);
			if (isStopChar && count >= maxLen) {
				if (c != '\n') {
					b.append(c);
				}
				return b.toString();
			} else {
				b.append(c);
				count++;
			}
		}

		return b.toString();
	}

	public static String trimNonAsciiFromEnd(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		int end = input.length();

		// Move backward until we find an ASCII character
		while (end > 0) {
			char c = input.charAt(end - 1);
			if (c <= 127) { // ASCII range
				break;
			}
			end--;
		}

		return input.substring(0, end);
	}

	public static boolean isNumber(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		try {
			// Integer.parseInt(text);
			Double.parseDouble(text);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String randomBase64Str(int len) {
		int inputBytes = len;
		byte[] randomBytes = new byte[inputBytes];
		RANDOM.nextBytes(randomBytes);
		return Base64.getEncoder().encodeToString(randomBytes).toUpperCase().substring(0, len);
	}

	public static String trimToWords(String text, int words) {
		String s = "Hello   world test";
		StringBuilder b = new StringBuilder();
		try (Scanner scanner = new Scanner(text)) {
			int i = 0;
			for (MatchResult result : scanner.findAll(WORD_REGEX + "|" + OTHER_REGEX).toList()) {
				String p = result.group();
				if (!p.trim().isBlank()) {
					i++;
				}
				b.append(p);
				if (i >= words) {
					break;
				}
			}
		}

		return b.toString().trim();
	}

	/**
	 * Checks whether the input text contains more non-ascii characters vs ascii
	 * characters in order to determine whether this is a logographical text.
	 * 
	 * @param text
	 * @return
	 */
	public static boolean isLogoGraphical(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}

		int asciiCount = 0;
		int nonAsciiCount = 0;

		for (char c : text.toCharArray()) {
			if (c <= 127) {
				asciiCount++;
			} else {
				nonAsciiCount++;
			}
		}

		// Avoid division by zero
		if (asciiCount + nonAsciiCount == 0) {
			return false;
		}

		double asciiRatio = (double) asciiCount / nonAsciiCount;
		return asciiRatio < 1;
	}

	/**
	 * Checks whether the input text only contains ascii characters.
	 * 
	 * @param text
	 * @return
	 */
	public static boolean isAscii(String text) {
		if (text == null) {
			return false;
		}
		if (text.isBlank()) {
			return true;
		}

		int asciiCount = 0;
		int nonAsciiCount = 0;

		for (char c : text.toCharArray()) {
			if (c <= 255) {
				asciiCount++;
			} else {
				nonAsciiCount++;
			}
		}

		return nonAsciiCount == 0;
	}

	public static boolean isEnglish(String text) {
		if (text == null) {
			return false;
		}
		text = text.toLowerCase();
		for (String word : List.of("the", "it", "he", "she", "be", "have", "that", "and")) {
			if (text.contains(" " + word + " ")) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean hasWord(String text, String word) {
		String wordNeedle = word.toLowerCase();
		wordNeedle = wordNeedle.substring(0, wordNeedle.length() - 2);
		return text.toLowerCase().contains(wordNeedle);
	}


}
