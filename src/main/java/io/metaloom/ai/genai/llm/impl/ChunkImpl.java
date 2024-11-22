package io.metaloom.ai.genai.llm.impl;

import io.metaloom.ai.genai.llm.Chunk;

public class ChunkImpl implements Chunk {

	private String token;

	public ChunkImpl(String token) {
		this.token = token;
	}

	@Override
	public String toString() {
		return token;
	}

}
