package io.metaloom.ai.genai.mockllm;

import java.util.List;

import io.vertx.core.json.JsonObject;

/**
 * Represents a pre-programmed response for the mock LLM server.
 * Supported variants:
 * <ul>
 *   <li>{@link SuccessResponse} – plain text chat completion</li>
 *   <li>{@link TruncatedResponse} – chat completion with finish_reason="length"</li>
 *   <li>{@link StructuredResponse} – chat completion whose content is a JSON string (structured output)</li>
 *   <li>{@link ToolCallsResponse} – finish_reason=tool_calls with one or more function calls</li>
 *   <li>{@link ErrorResponse} – HTTP error with an OpenAI-compatible error body</li>
 * </ul>
 */
public sealed interface MockResponse {

    /**
     * A successful chat completion response returning the given text content.
     */
    record SuccessResponse(String text) implements MockResponse {}

    /**
     * A successful chat completion response with finish_reason="length", simulating
     * a truncated output due to token limit being reached (soft error).
     */
    record TruncatedResponse(String text) implements MockResponse {}

    /**
     * A structured-output chat completion whose content is a serialised JSON string.
     * Semantically identical to {@link SuccessResponse} on the wire; the separate type
     * makes test intent clearer.
     *
     * @param json the JSON object that will be serialised as the message content
     */
    record StructuredResponse(JsonObject json) implements MockResponse {}

    /**
     * A single tool / function call the model wants to make.
     *
     * @param id            tool-call id (e.g. {@code "call_abc123"})
     * @param functionName  name of the function to invoke
     * @param argumentsJson raw JSON string of the function arguments (e.g. {@code "{\"city\":\"Vienna\"}"})
     */
    record MockToolCall(String id, String functionName, String argumentsJson) {

        /** Convenience factory – generates a random id. */
        public static MockToolCall of(String functionName, JsonObject arguments) {
            String id = "call_" + Long.toHexString(System.nanoTime());
            return new MockToolCall(id, functionName, arguments.encode());
        }
    }

    /**
     * A response that triggers tool / function calling (finish_reason = "tool_calls").
     * The message content will be {@code null} as per the OpenAI spec.
     *
     * @param toolCalls one or more tool calls the model wants to make
     */
    record ToolCallsResponse(List<MockToolCall> toolCalls) implements MockResponse {

        /** Convenience factory for a single tool call. */
        public static ToolCallsResponse of(MockToolCall toolCall) {
            return new ToolCallsResponse(List.of(toolCall));
        }
    }

    /**
     * An HTTP error response with an OpenAI-compatible error body.
     *
     * @param statusCode HTTP status code, e.g. 429 or 400
     * @param type       OpenAI error type, e.g. "invalid_request_error"
     * @param code       OpenAI error code, e.g. "invalid_prompt"
     * @param message    Human-readable error message
     * @param param      Optional parameter name that caused the error (may be null)
     */
    record ErrorResponse(
            int statusCode,
            String type,
            String code,
            String message,
            String param) implements MockResponse {

        /** Convenience constructor without a param field. */
        public ErrorResponse(int statusCode, String type, String code, String message) {
            this(statusCode, type, code, message, null);
        }

        /** Convenience factory for rate-limit errors (429). */
        public static ErrorResponse rateLimitError() {
            return new ErrorResponse(429, "requests", "rate_limit_exceeded", "Rate limit exceeded");
        }

        /** Convenience factory for a generic server error (500). */
        public static ErrorResponse serverError(String message) {
            return new ErrorResponse(500, "server_error", "internal_server_error", message);
        }

        /** Convenience factory matching the OpenAI invalid_prompt error format (400). */
        public static ErrorResponse invalidPrompt(String message) {
            return new ErrorResponse(400, "invalid_request_error", "invalid_prompt", message, "prompt");
        }
    }
}
