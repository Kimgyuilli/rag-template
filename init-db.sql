-- PostgreSQL 초기화 스크립트
-- Docker 컨테이너 시작 시 자동 실행됨

-- pgvector extension 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 확인 메시지
DO $$
BEGIN
    RAISE NOTICE 'pgvector extension created successfully';
END $$;

-- document_chunks 테이블 생성
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI text-embedding-ada-002 dimension
    source_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
-- 1. 벡터 유사도 검색용 IVFFlat 인덱스
-- 참고: 데이터가 충분히 쌓인 후에 생성하는 것이 효율적
-- 초기에는 주석 처리하고, 데이터 삽입 후 수동으로 생성 권장
-- CREATE INDEX idx_embedding ON document_chunks
-- USING ivfflat (embedding vector_cosine_ops)
-- WITH (lists = 100);

-- 2. 문서 ID 인덱스 (특정 문서의 모든 청크 조회용)
CREATE INDEX IF NOT EXISTS idx_document_id ON document_chunks(document_id);

-- 3. 생성 시간 인덱스 (시간 범위 검색용)
CREATE INDEX IF NOT EXISTS idx_created_at ON document_chunks(created_at);

-- 확인 메시지
DO $$
BEGIN
    RAISE NOTICE 'document_chunks table and indexes created successfully';
END $$;

-- 테스트 데이터 삽입 (선택사항)
-- INSERT INTO document_chunks (document_id, filename, chunk_index, content, source_type)
-- VALUES
--     ('test-doc-001', 'sample.txt', 0, '이것은 테스트 문서입니다.', 'txt'),
--     ('test-doc-001', 'sample.txt', 1, '두 번째 청크입니다.', 'txt');

-- 테이블 정보 확인
SELECT
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE tablename = 'document_chunks';

-- Extension 확인
SELECT * FROM pg_extension WHERE extname = 'vector';
