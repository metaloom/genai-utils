package io.metaloom.ai.genai.rag.index;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.metaloom.ai.genai.rag.index.impl.LuceneRagIndex;
import io.metaloom.ai.genai.rag.index.impl.RagDocumentImpl;

public class RagIndexTest {

	public static final float[] queryVector = new float[] { 0.98f, 0.01f };

	// Goal vector is very close to our actual query vector
	public static final float[] goalVector = new float[] { queryVector[0] - 0.01f, queryVector[1] + 0.01f };
	
	public static final float[] farVector1 = new float[] { queryVector[0] - 10.01f, queryVector[1] + 20.01f };
	public static final float[] farVector2 = new float[] { queryVector[0] - 10.01f, queryVector[1] + 20.01f };

	@Test
	public void testIndex() throws IOException {
		RagIndex index = new LuceneRagIndex(Path.of("target", "index"), 2);

		index.writer(w -> {
			try {
				index.indexDocument(w, new RagDocumentImpl("Hallo Welt 1", RagVector.of(goalVector)));
				index.indexDocument(w, new RagDocumentImpl("Hallo Welt 2", RagVector.of(farVector1)));
				index.indexDocument(w, new RagDocumentImpl("Hallo Welt 3", RagVector.of(farVector2)));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		RagQueryResult result = index.query(RagVector.of(queryVector), 2, 0);
		for (RagHit entry : result.entries()) {
			System.out.println(entry.text());
		}
	}
}
