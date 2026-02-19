package com.example.rag.document;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

	private final VectorStore vectorStore;
	private final TokenTextSplitter splitter = new TokenTextSplitter(512, 50, 5, 1000, true);

	public DocumentService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	public void ingest(String title, String content, String category) {
		Document document = new Document(content, Map.of(
				"title", title,
				"category", category));

		List<Document> chunks = splitter.apply(List.of(document));
		vectorStore.add(chunks);
	}
}
