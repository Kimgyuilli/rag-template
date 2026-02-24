package com.example.rag.document;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.rag.document.dto.DocumentSummary;

import lombok.RequiredArgsConstructor;

/**
 * vector_store 테이블에서 문서 메타데이터를 조회하는 저장소.
 * Spring AI가 관리하는 테이블이라 JPA를 사용할 수 없어 JdbcTemplate으로 직접 쿼리한다.
 */
@Repository
@RequiredArgsConstructor
public class DocumentRepository {

	private final JdbcTemplate jdbcTemplate;

	/** documentId별 문서 목록 조회. */
	List<DocumentSummary> findAllGroupedByDocumentId() {
		return jdbcTemplate.query("""
				SELECT metadata->>'documentId' AS document_id,
				       metadata->>'title' AS title,
				       metadata->>'category' AS category,
				       COUNT(*) AS chunk_count
				FROM vector_store
				WHERE metadata->>'documentId' IS NOT NULL
				GROUP BY document_id, title, category
				ORDER BY title
				""",
				(rs, rowNum) -> new DocumentSummary(
						UUID.fromString(rs.getString("document_id")),
						rs.getString("title"),
						rs.getString("category"),
						rs.getInt("chunk_count")));
	}

	/** documentId에 해당하는 청크의 content, title, category 조회. */
	List<ChunkRow> findChunksByDocumentId(UUID documentId) {
		return jdbcTemplate.query("""
				SELECT content, metadata->>'title' AS title, metadata->>'category' AS category
				FROM vector_store
				WHERE metadata->>'documentId' = ?
				""",
				(rs, rowNum) -> new ChunkRow(
						rs.getString("content"),
						rs.getString("title"),
						rs.getString("category")),
				documentId.toString());
	}

	/** documentId에 해당하는 청크 ID 목록 조회. */
	List<String> findChunkIdsByDocumentId(UUID documentId) {
		return jdbcTemplate.queryForList("""
				SELECT id FROM vector_store WHERE metadata->>'documentId' = ?
				""",
				String.class, documentId.toString());
	}

	record ChunkRow(String content, String title, String category) {}
}
