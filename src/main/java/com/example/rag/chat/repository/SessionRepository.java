package com.example.rag.chat.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.rag.chat.dto.vo.SessionSummary;

import lombok.RequiredArgsConstructor;

/**
 * SPRING_AI_CHAT_MEMORY 테이블에서 세션 목록을 조회한다.
 */
@Repository
@RequiredArgsConstructor
public class SessionRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 세션 목록 조회. conversation_id별 첫 번째 USER 메시지를 제목으로 사용한다.
	 * 최신 세션 순으로 정렬.
	 */
	public List<SessionSummary> findAllSessions() {
		return jdbcTemplate.query("""
				SELECT DISTINCT ON (conversation_id)
				       conversation_id, content, "timestamp"
				FROM   spring_ai_chat_memory
				WHERE  type = 'USER'
				ORDER  BY conversation_id, "timestamp" ASC
				""",
				(rs, rowNum) -> new SessionSummary(
						rs.getString("conversation_id"),
						truncate(rs.getString("content"), 50),
						rs.getTimestamp("timestamp").toLocalDateTime()))
				.stream()
				.sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
				.toList();
	}

	private String truncate(String text, int maxLen) {
		if (text == null) return "";
		return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
	}
}
