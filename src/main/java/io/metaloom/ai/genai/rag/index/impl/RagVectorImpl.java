package io.metaloom.ai.genai.rag.index.impl;

import io.metaloom.ai.genai.rag.index.RagVector;

public class RagVectorImpl implements RagVector {

	private float[] components;

	public RagVectorImpl(float[] components) {
		this.components = components;
	}

	@Override
	public float[] components() {
		return components;
	}

}
