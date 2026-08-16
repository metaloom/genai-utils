package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.Chunk;
import io.metaloom.ai.genai.llm.TokenUsage;

public class ChunkImpl implements Chunk {

	private String token;

	boolean thinking;

	private final TokenUsage usage;

	public ChunkImpl(String token, boolean thinking) {
		this(token, thinking, TokenUsage.NONE);
	}

	public ChunkImpl(String token, boolean thinking, TokenUsage usage) {
		this.token = token;
		this.thinking = thinking;
		this.usage = usage == null ? TokenUsage.NONE : usage;
	}

	/**
	 * Create the final, text-less chunk that carries the token accounting of the call.
	 *
	 * @param usage
	 * @return
	 */
	public static ChunkImpl usageChunk(TokenUsage usage) {
		return new ChunkImpl("", false, usage);
	}

	@Override
	public String toString() {
		return token;
	}

	@Override
	public boolean isThinking() {
		return thinking;
	}

	@Override
	public TokenUsage usage() {
		return usage;
	}

}
