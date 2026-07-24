package io.metaloom.ai.genai.rag.index.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene103.Lucene103Codec;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.metaloom.ai.genai.rag.index.RagDocument;
import io.metaloom.ai.genai.rag.index.RagIndex;
import io.metaloom.ai.genai.rag.index.RagQueryResult;
import io.metaloom.ai.genai.rag.index.RagVector;

public class LuceneRagIndex implements RagIndex {

	public static final String VECTOR_FIELD_KEY = "embedding";

	public static final String TEXT_FIELD_KEY = "text";

	public static final Logger log = LoggerFactory.getLogger(LuceneRagIndex.class);

	private Codec codec;

	private Path indexPath;

	public LuceneRagIndex(Path indexPath, int embeddingSize) {
		this.indexPath = indexPath;
		this.codec = codec(embeddingSize);
	}

	private Codec codec(int embeddingSize) {
		return new Lucene103Codec() {
			@Override
			public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
				int maxConn = 200;
				int beamWidth = 100;
				KnnVectorsFormat knnFormat = new Lucene99HnswVectorsFormat(maxConn, beamWidth);
				return new HighDimensionKnnVectorsFormat(knnFormat, embeddingSize);
			}
		};
	}

	@Override
	public void writer(Consumer<IndexWriter> consumer) throws IOException {
		try (MMapDirectory dir = new MMapDirectory(indexPath)) {
			try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
				consumer.accept(writer);
			}
		}
	}

	@Override
	public RagQueryResult query(RagVector vector, int limit, float scoreThreshold) throws IOException {
		try (MMapDirectory dir = new MMapDirectory(indexPath)) {
			try (IndexReader reader = DirectoryReader.open(dir)) {
				return queryIndex(reader, vector, limit, scoreThreshold);
			}
		}
	}

	private RagQueryResult queryIndex(IndexReader reader, RagVector vector, int limit, float scoreThreshold) throws IOException {
		IndexSearcher searcher = new IndexSearcher(reader);
		float[] queryVector = vector.components();

		TopDocs results = searcher.search(new KnnFloatVectorQuery(VECTOR_FIELD_KEY, queryVector, limit), 10);

		return from(reader, results, scoreThreshold);
	}

	private RagQueryResult from(IndexReader reader, TopDocs results, float scoreThreshold) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Hits: {}", results.totalHits);
		}
		RagQueryResult result = new RagQueryResultImpl();
		StoredFields storedFields = reader.storedFields();

		for (ScoreDoc sdoc : results.scoreDocs) {
			if (sdoc.score < scoreThreshold) {
				if (log.isTraceEnabled()) {
					log.trace("Omitting doc since its score is below our threshold of {}", scoreThreshold);
				}
				continue;
			}
			try {
				Document doc = storedFields.document(sdoc.doc);
				IndexableField field = doc.getField(TEXT_FIELD_KEY);
				if (field != null && field instanceof StringField) {
					StoredField textField = (StoredField) field;
					if (log.isDebugEnabled()) {
						log.debug("Found: " + textField.stringValue() + " = " + String.format("%.1f", sdoc.score));
					}
					result.add(new RagHitImpl(sdoc.score, textField.stringValue()));
				} else {
					if (log.isWarnEnabled()) {
						log.warn("Document does not provide an id field. Will thus be omitted from query result.", doc);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	@Override
	public void delete() throws IOException {
		File file = indexPath.toFile();
		if (file.exists()) {
			FileUtils.deleteDirectory(file);
		}
	}

	@Override
	public void indexDocument(IndexWriter writer, RagDocument ragDoc) throws IOException {
		float[] vector = ragDoc.vector().components();
		Document doc = new Document();
		doc.add(new StringField(TEXT_FIELD_KEY, ragDoc.text(), Store.YES));
		doc.add(new KnnFloatVectorField(VECTOR_FIELD_KEY, vector));
		writer.addDocument(doc);
	}

}
