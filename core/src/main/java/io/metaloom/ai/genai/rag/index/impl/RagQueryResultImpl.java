package io.metaloom.ai.genai.rag.index.impl;

import java.util.ArrayList;
import java.util.List;

import io.metaloom.ai.genai.rag.index.RagHit;
import io.metaloom.ai.genai.rag.index.RagQueryResult;

public class RagQueryResultImpl implements RagQueryResult {

	private final List<RagHit> entries = new ArrayList<>();

	public RagQueryResultImpl() {

	}

	@Override
	public void add(RagHit entry) {
		entries.add(entry);
	}

	@Override
	public List<RagHit> entries() {
		return entries;
	}
}
