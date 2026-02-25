CREATE INDEX IF NOT EXISTS idx_vector_store_document_id ON vector_store ((metadata->>'documentId'));

-- 키워드 검색용 tsvector 컬럼 추가
ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- GIN 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_vector_store_content_tsv ON vector_store USING GIN (content_tsv);

-- 기존 데이터 tsvector 갱신
UPDATE vector_store SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;
