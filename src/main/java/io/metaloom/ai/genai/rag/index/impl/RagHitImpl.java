package io.metaloom.ai.genai.rag.index.impl;

import io.metaloom.ai.genai.rag.index.RagHit;

public class RagHitImpl implements RagHit {

	private final float score;
	private final String text;

	public RagHitImpl(float score, String text) {
		this.score = score;
		this.text = text;
	}

	@Override
	public float score() {
		return score;
	}

	@Override
	public String text() {
		return text;
	}

}
