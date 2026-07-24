package io.metaloom.ai.genai.rag.index;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.lucene.index.IndexWriter;

public interface RagIndex {

	RagQueryResult query(RagVector vector, int limit, float scoreThreshold) throws IOException;

	void delete() throws IOException;

	void writer(Consumer<IndexWriter> consumer) throws IOException;

	void indexDocument(IndexWriter w, RagDocument doc) throws IOException;

	
	
}
