package io.metaloom.ai.genai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class TextUtils {

	public TextUtils() {
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
}
