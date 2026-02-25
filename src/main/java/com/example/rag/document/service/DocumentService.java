package com.example.rag.document.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.rag.document.repository.DocumentRepository;
import com.example.rag.document.repository.DocumentRepository.ChunkRow;
import com.example.rag.document.dto.vo.DocumentDetail;
import com.example.rag.document.dto.vo.DocumentSummary;

import lombok.RequiredArgsConstructor;

/**
 * 문서 처리 서비스.
 * 원본 문서를 청크로 분할한 뒤 벡터 저장소에 임베딩하여 저장한다.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

	private final VectorStore vectorStore;
	private final DocumentRepository documentRepository;
	/** 문서를 토큰 단위로 청크 분할 (최대 512토큰, 최소 50자, 겹침 5자). */
	private final TokenTextSplitter splitter = new TokenTextSplitter(512, 50, 5, 1000, true);

	/**
	 * 문서를 메타데이터와 함께 생성하고, 청크 분할 후 벡터 저장소에 저장한다.
	 *
	 * @return 생성된 documentId
	 */
	public UUID ingest(String title, String content, String category) {
		UUID documentId = UUID.randomUUID();
		return ingest(documentId, title, content, category);
	}

	private UUID ingest(UUID documentId, String title, String content, String category) {
		Document document = new Document(content, Map.of(
				"title", title,
				"category", category,
				"documentId", documentId.toString()));

		List<Document> chunks = splitter.apply(List.of(document));
		vectorStore.add(chunks);
		return documentId;
	}

	/** 문서 목록 조회 (documentId별 그룹). */
	public List<DocumentSummary> list() {
		return documentRepository.findAllGroupedByDocumentId();
	}

	/** 문서 상세 조회: 청크 내용을 합쳐 원본 텍스트를 재조합한다. */
	public DocumentDetail getById(UUID documentId) {
		List<ChunkRow> rows = documentRepository.findChunksByDocumentId(documentId);

		if (rows.isEmpty()) {
			return null;
		}

		String combinedContent = String.join("\n", rows.stream().map(ChunkRow::content).toList());
		ChunkRow first = rows.getFirst();
		return new DocumentDetail(documentId, first.title(), combinedContent, first.category(), rows.size());
	}

	/** 문서 삭제: documentId에 해당하는 모든 청크를 삭제한다. */
	public boolean delete(UUID documentId) {
		List<String> chunkIds = documentRepository.findChunkIdsByDocumentId(documentId);

		if (chunkIds.isEmpty()) {
			return false;
		}

		vectorStore.delete(chunkIds);
		return true;
	}

	/** 문서 수정: 기존 청크를 삭제하고 동일 documentId로 재등록한다. */
	public boolean update(UUID documentId, String title, String content, String category) {
		if (!delete(documentId)) {
			return false;
		}
		ingest(documentId, title, content, category);
		return true;
	}

}
