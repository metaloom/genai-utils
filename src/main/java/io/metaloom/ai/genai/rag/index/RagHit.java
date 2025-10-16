package io.metaloom.ai.genai.rag.index;

public interface RagHit {

	float score();

	String text();

}
