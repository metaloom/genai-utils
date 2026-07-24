package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.Chunk;

public class ChunkImpl implements Chunk {

	private String token;
	
	boolean thinking;

	public ChunkImpl(String token, boolean thinking) {
		this.token = token;
		this.thinking= thinking;
	}

	@Override
	public String toString() {
		return token;
	}
	
	@Override
	public boolean isThinking() {
		return thinking;
	}

}
