package io.metaloom.ai.genai.utils;

public final class ReasoningUtils {

    private ReasoningUtils() {
    }

    public static boolean isThinkStart(String token) {
        return "<think>".equalsIgnoreCase(token);
    }

    public static boolean isThinkEnd(String token) {
        return "</think>".equalsIgnoreCase(token);
    }

    public static boolean isThinkingStartEndToken(String token) {
        return isThinkStart(token) || isThinkEnd(token);
    }
}
