package io.metaloom.ai.genai.rag.index.impl;

import io.metaloom.ai.genai.rag.index.RagDocument;
import io.metaloom.ai.genai.rag.index.RagVector;

public class RagDocumentImpl implements RagDocument {

	private String text;
	private RagVector vector;

	public RagDocumentImpl(String text, RagVector vector) {
		this.text = text;
		this.vector = vector;
	}

	@Override
	public RagVector vector() {
		return vector;
	}

	@Override
	public String text() {
		return text;
	}

}
