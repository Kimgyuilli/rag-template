CREATE INDEX IF NOT EXISTS idx_vector_store_document_id ON vector_store ((metadata->>'documentId'));

-- 키워드 검색용 tsvector 컬럼 추가
ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- GIN 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_vector_store_content_tsv ON vector_store USING GIN (content_tsv);

-- 기존 데이터 tsvector 갱신
UPDATE vector_store SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;

-- INSERT/UPDATE 시 자동 갱신 트리거
CREATE OR REPLACE FUNCTION vector_store_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_vector_store_tsv'
    ) THEN
        CREATE TRIGGER trg_vector_store_tsv
            BEFORE INSERT OR UPDATE OF content ON vector_store
            FOR EACH ROW
            EXECUTE FUNCTION vector_store_tsv_trigger();
    END IF;
END;
$$;
