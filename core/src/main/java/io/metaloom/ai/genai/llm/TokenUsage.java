package io.metaloom.ai.genai.llm;

/**
 * Token accounting of a single LLM call. The values are taken from the {@code usage} object that
 * OpenAI-compatible servers (vLLM, llama.cpp, Ollama {@code /v1}, OpenAI) attach to a chat
 * completion response.
 *
 * <p>
 * Servers only report a subset of these fields. Everything that was not reported is {@code 0} — use
 * {@link #isReported()} to tell "the server sent no accounting at all" apart from a genuine count.
 *
 * @param promptTokens       tokens consumed by the request (prompt / input tokens)
 * @param completionTokens   tokens produced by the model (completion / output tokens)
 * @param totalTokens        prompt + completion tokens as reported by the server
 * @param reasoningTokens    subset of the completion tokens spent on the reasoning trace
 *                           ({@code completion_tokens_details.reasoning_tokens}), 0 when not
 *                           reported
 * @param cachedPromptTokens subset of the prompt tokens served from the server prefix cache
 *                           ({@code prompt_tokens_details.cached_tokens}), 0 when not reported
 */
public record TokenUsage(
	long promptTokens,
	long completionTokens,
	long totalTokens,
	long reasoningTokens,
	long cachedPromptTokens) {

	/**
	 * Usage of a call for which the server did not report any token accounting.
	 */
	public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0, 0);

	public TokenUsage {
		// Not every server sends total_tokens - derive it so consumers can always read one value.
		if (totalTokens == 0) {
			totalTokens = promptTokens + completionTokens;
		}
	}

	/**
	 * Create a usage with prompt and completion tokens only. The total is derived.
	 *
	 * @param promptTokens
	 * @param completionTokens
	 * @return
	 */
	public static TokenUsage of(long promptTokens, long completionTokens) {
		return new TokenUsage(promptTokens, completionTokens, 0, 0, 0);
	}

	/**
	 * Check whether the server reported any token counts at all.
	 *
	 * @return true when at least one counter is non-zero
	 */
	public boolean isReported() {
		return totalTokens > 0 || promptTokens > 0 || completionTokens > 0;
	}

	/**
	 * Add up two usages. Handy to accumulate the token spend of a multi-turn agent loop.
	 *
	 * @param other usage to add, may be null
	 * @return a new, combined usage
	 */
	public TokenUsage plus(TokenUsage other) {
		if (other == null) {
			return this;
		}
		return new TokenUsage(
			promptTokens + other.promptTokens,
			completionTokens + other.completionTokens,
			totalTokens + other.totalTokens,
			reasoningTokens + other.reasoningTokens,
			cachedPromptTokens + other.cachedPromptTokens);
	}

	@Override
	public String toString() {
		return "[prompt:" + promptTokens + ",completion:" + completionTokens + ",total:" + totalTokens
			+ ",reasoning:" + reasoningTokens + ",cached:" + cachedPromptTokens + "]";
	}
}
