package io.metaloom.ai.genai.rag.index;

import io.metaloom.ai.genai.rag.index.impl.RagVectorImpl;

public interface RagVector {

	float[] components();

	public static RagVector of(float[] vector) {
		return new RagVectorImpl(vector);
	}
}
