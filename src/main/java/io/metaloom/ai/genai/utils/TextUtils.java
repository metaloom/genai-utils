package io.metaloom.ai.genai.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;

public final class TextUtils {

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

}
