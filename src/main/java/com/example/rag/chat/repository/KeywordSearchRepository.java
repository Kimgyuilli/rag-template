package com.example.rag.chat.repository;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * tsvector 기반 키워드 검색 저장소.
 * 'simple' 설정으로 한국어 공백 분리 검색을 지원한다.
 */
@Repository
@RequiredArgsConstructor
public class KeywordSearchRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 키워드 검색으로 관련 문서를 조회한다.
	 * 쿼리를 공백으로 분리하여 OR 검색한다.
	 *
	 * @param query    검색 쿼리
	 * @param topK     최대 결과 수
	 * @param category 카테고리 필터 (null이면 전체 검색)
	 * @return ts_rank 순으로 정렬된 Document 목록
	 */
	public List<Document> search(String query, int topK, String category) {
		String tsQuery = buildTsQuery(query);
		if (tsQuery.isEmpty()) {
			return List.of();
		}

		String sql;
		Object[] params;

		if (category != null && !category.isBlank()) {
			sql = """
					SELECT id, content, metadata
					FROM vector_store
					WHERE content_tsv @@ to_tsquery('simple', ?)
					  AND metadata->>'category' = ?
					ORDER BY ts_rank(content_tsv, to_tsquery('simple', ?)) DESC
					LIMIT ?
					""";
			params = new Object[]{tsQuery, category, tsQuery, topK};
		} else {
			sql = """
					SELECT id, content, metadata
					FROM vector_store
					WHERE content_tsv @@ to_tsquery('simple', ?)
					ORDER BY ts_rank(content_tsv, to_tsquery('simple', ?)) DESC
					LIMIT ?
					""";
			params = new Object[]{tsQuery, tsQuery, topK};
		}

		return jdbcTemplate.query(sql, (rs, rowNum) -> {
			String id = rs.getString("id");
			String content = rs.getString("content");
			Document doc = new Document(id, content, java.util.Map.of());
			return doc;
		}, params);
	}

	/**
	 * 공백으로 분리된 단어를 OR(|) 연산자로 결합하여 tsquery 문자열을 생성한다.
	 */
	private String buildTsQuery(String query) {
		if (query == null || query.isBlank()) {
			return "";
		}
		String[] words = query.strip().split("\\s+");
		return String.join(" | ", words);
	}
}
