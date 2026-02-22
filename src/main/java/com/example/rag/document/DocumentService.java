package com.example.rag.document;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 문서 처리 서비스.
 * 원본 문서를 청크로 분할한 뒤 벡터 저장소에 임베딩하여 저장한다.
 */
@Service
public class DocumentService {

	private final VectorStore vectorStore;
	/** 문서를 토큰 단위로 청크 분할 (최대 512토큰, 최소 50자, 겹침 5자). */
	private final TokenTextSplitter splitter = new TokenTextSplitter(512, 50, 5, 1000, true);

	public DocumentService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	/**
	 * 문서를 메타데이터와 함께 생성하고, 청크 분할 후 벡터 저장소에 저장한다.
	 *
	 * @param title    문서 제목
	 * @param content  문서 본문
	 * @param category 문서 카테고리
	 */
	public void ingest(String title, String content, String category) {
		Document document = new Document(content, Map.of(
				"title", title,
				"category", category));

		List<Document> chunks = splitter.apply(List.of(document));
		vectorStore.add(chunks);
	}
}
