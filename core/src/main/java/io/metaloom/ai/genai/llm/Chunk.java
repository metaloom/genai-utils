package io.metaloom.ai.genai.llm;

/**
 * Representation of a streamed text fragment when using streaming LLM API.
 */
public interface Chunk {

	boolean isThinking();

	/**
	 * Token accounting of the call. Regular text chunks carry {@link TokenUsage#NONE}; a server that
	 * reports usage for a streamed call sends it after the last text fragment, which the provider
	 * surfaces as one final chunk with empty text and the accounting attached.
	 *
	 * @return never null
	 */
	default TokenUsage usage() {
		return TokenUsage.NONE;
	}

}
