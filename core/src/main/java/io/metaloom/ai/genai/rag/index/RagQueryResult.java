package io.metaloom.ai.genai.rag.index;

import java.util.List;

public interface RagQueryResult {

	void add(RagHit entry);

	List<RagHit> entries();

}
