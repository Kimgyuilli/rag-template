package com.example.rag.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * tsvector 트리거 초기화.
 * schema.sql에서는 PL/pgSQL 함수를 실행할 수 없으므로 애플리케이션 시작 후 직접 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TsvectorInitializer {

	private final JdbcTemplate jdbcTemplate;

	@EventListener(ApplicationReadyEvent.class)
	public void createTrigger() {
		jdbcTemplate.execute("""
				CREATE OR REPLACE FUNCTION vector_store_tsv_trigger() RETURNS trigger AS $$
				BEGIN
				    NEW.content_tsv := to_tsvector('simple', NEW.content);
				    RETURN NEW;
				END;
				$$ LANGUAGE plpgsql
				""");

		// 트리거가 없으면 생성
		Boolean exists = jdbcTemplate.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM pg_trigger WHERE tgname = 'trg_vector_store_tsv')",
				Boolean.class);

		if (!Boolean.TRUE.equals(exists)) {
			jdbcTemplate.execute("""
					CREATE TRIGGER trg_vector_store_tsv
					    BEFORE INSERT OR UPDATE OF content ON vector_store
					    FOR EACH ROW
					    EXECUTE FUNCTION vector_store_tsv_trigger()
					""");
			log.info("tsvector 트리거 생성 완료");
		}
	}
}
