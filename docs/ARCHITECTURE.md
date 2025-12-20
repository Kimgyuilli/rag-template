# RAG 시스템 아키텍처 문서

## 목차

1. [시스템 개요](#시스템-개요)
2. [아키텍처 설계](#아키텍처-설계)
3. [데이터 플로우](#데이터-플로우)
4. [핵심 컴포넌트](#핵심-컴포넌트)
5. [데이터베이스 설계](#데이터베이스-설계)
6. [API 설계](#api-설계)
7. [구현 전략](#구현-전략)
8. [설계 결정 사항](#설계-결정-사항)

---

## 시스템 개요

### 목적

이 시스템은 **RAG(Retrieval-Augmented Generation)** 기술을 활용한 고객 지원 챗봇입니다. 사용자가 업로드한 문서를 기반으로 질문에 답변하며, 추상화 라이브러리 없이 직접 구현하여 RAG의 핵심 원리를 학습합니다.

### 핵심 기능

1. **문서 관리**: 텍스트 파일 업로드 및 처리
2. **시맨틱 검색**: 벡터 기반 유사도 검색
3. **컨텍스트 기반 응답**: LLM을 활용한 정확한 답변 생성
4. **소스 추적**: 답변의 출처 정보 제공

### 설계 원칙

- **학습 우선**: 추상화보다 명확한 구현
- **단순성**: MVP 범위에 집중
- **확장 가능성**: 향후 기능 추가를 고려한 설계
- **비용 효율**: OpenAI API 호출 최소화

---

## 아키텍처 설계

### 전체 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Layer                              │
│                  (HTTP Client / REST API)                        │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────────────┐
│                    Controller Layer                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │  ChatController  │  │DocumentController│  │HealthCtrl    │  │
│  │  /api/v1/chat    │  │  /api/v1/docs    │  │ /api/v1/     │  │
│  └────────┬─────────┘  └────────┬─────────┘  │  health      │  │
└───────────┼─────────────────────┼─────────────┴──────────────────┘
            │                     │
┌───────────┴─────────────────────┴────────────────────────────────┐
│                      Service Layer                               │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │   ChatService    │  │ DocumentService  │                     │
│  │                  │  │                  │                     │
│  │  - RAG Pipeline  │  │ - File Upload    │                     │
│  │  - Query Embed   │  │ - Text Extract   │                     │
│  │  - Retrieval     │  │ - Chunking       │                     │
│  │  - LLM Call      │  │ - Embedding      │                     │
│  └────────┬─────────┘  └────────┬─────────┘                     │
│           │                     │                                │
│  ┌────────┴─────────────────────┴─────────┐                     │
│  │         Common Services                │                     │
│  ├──────────────────┬──────────────────────┤                     │
│  │ EmbeddingService │ VectorSearchService  │                     │
│  │                  │                      │                     │
│  │ - OpenAI API     │ - Similarity Search  │                     │
│  │ - Batch Process  │ - Top-K Retrieval    │                     │
│  │ - Error Handling │ - Threshold Filter   │                     │
│  └──────────────────┴──────────────────────┘                     │
│  ┌──────────────────┐                                            │
│  │   TextChunker    │                                            │
│  │ - Recursive Split│                                            │
│  │ - Token Overlap  │                                            │
│  └──────────────────┘                                            │
└───────────────────────┬──────────────────────────────────────────┘
                        │
┌───────────────────────┴──────────────────────────────────────────┐
│                   Repository Layer                               │
│  ┌──────────────────────────────────────────┐                   │
│  │      DocumentChunkRepository             │                   │
│  │      (Spring Data JPA)                   │                   │
│  └────────────────────┬─────────────────────┘                   │
└───────────────────────┴──────────────────────────────────────────┘
                        │
┌───────────────────────┴──────────────────────────────────────────┐
│                  External Services                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  PostgreSQL     │  │   OpenAI API    │  │  File System    │ │
│  │  + pgvector     │  │                 │  │                 │ │
│  │                 │  │ - Embeddings    │  │ - Uploaded      │ │
│  │ - Vector Store  │  │ - Chat Compl.   │  │   Documents     │ │
│  │ - Metadata      │  │                 │  │   (Optional)    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 레이어별 책임

| 레이어 | 책임 | 주요 기술 |
|-------|------|---------|
| **Controller** | HTTP 요청/응답 처리, 입력 검증 | Spring MVC, @RestController |
| **Service** | 비즈니스 로직, 오케스트레이션 | @Service, @Transactional |
| **Repository** | 데이터 접근, 쿼리 실행 | Spring Data JPA |
| **External** | 외부 서비스 통합 | OpenAI SDK, JDBC |

---

## 데이터 플로우

### 1. 문서 처리 파이프라인

```
[사용자]
   │
   │ (1) POST /api/v1/documents/upload (MultipartFile)
   ▼
[DocumentController]
   │
   │ (2) validateFile() → DocumentService.processDocument()
   ▼
[DocumentService]
   │
   ├─► (3) Apache Tika.parseToString() → 텍스트 추출
   │
   ├─► (4) TextChunker.chunk() → 청킹
   │       ├─ 1000 토큰 단위
   │       └─ 200 토큰 오버랩
   │
   ├─► (5) EmbeddingService.embed() → 임베딩 생성
   │       ├─ OpenAI API 호출 (text-embedding-ada-002)
   │       ├─ 배치 처리 (최대 100개)
   │       └─ 1536 차원 벡터 반환
   │
   └─► (6) DocumentChunkRepository.saveAll() → DB 저장
           └─ PostgreSQL pgvector에 저장
   │
   ▼
[응답]
   {
     "documentId": "abc123",
     "chunksCreated": 12,
     "status": "processed"
   }
```

### 2. 질의응답 파이프라인 (RAG)

```
[사용자]
   │
   │ (1) POST /api/v1/chat
   │     { "message": "비밀번호를 어떻게 재설정하나요?" }
   ▼
[ChatController]
   │
   │ (2) ChatService.chat()
   ▼
[ChatService]
   │
   ├─► (3) EmbeddingService.embed(question)
   │       └─ OpenAI API → 질문 임베딩 [1536]
   │
   ├─► (4) VectorSearchService.search(embedding)
   │       ├─ pgvector 코사인 유사도 검색
   │       ├─ Top-K=3 청크 검색
   │       └─ threshold > 0.7
   │       │
   │       └─ 반환: List<SearchResult>
   │           ├─ content: "비밀번호 재설정 방법..."
   │           ├─ filename: "manual.txt"
   │           ├─ chunkIndex: 5
   │           └─ similarity: 0.89
   │
   ├─► (5) PromptBuilder.build(question, searchResults)
   │       ├─ System Prompt: 역할 정의
   │       └─ User Prompt: 컨텍스트 + 질문
   │       │
   │       └─ 생성된 프롬프트:
   │           """
   │           [컨텍스트]
   │           파일: manual.txt (청크 5)
   │           내용: 비밀번호 재설정 방법...
   │
   │           [질문]
   │           비밀번호를 어떻게 재설정하나요?
   │           """
   │
   └─► (6) OpenAI Chat Completion API
           ├─ Model: gpt-3.5-turbo
           ├─ Temperature: 0.3
           └─ Max Tokens: 500
           │
           └─ 응답 생성
   │
   ▼
[응답]
   {
     "response": "비밀번호를 재설정하려면...",
     "sources": [
       {
         "filename": "manual.txt",
         "chunkIndex": 5,
         "similarity": 0.89
       }
     ]
   }
```

### 3. 시퀀스 다이어그램

```
사용자    Controller    ChatService    EmbeddingService    VectorSearch    OpenAI    DB
  │            │             │                  │                 │           │       │
  │─질문────────►│             │                  │                 │           │       │
  │            │─chat()─────►│                  │                 │           │       │
  │            │             │─embed(question)─►│                 │           │       │
  │            │             │                  │─API Call───────────────────►│       │
  │            │             │                  │◄────embedding────────────────│       │
  │            │             │◄─embedding───────│                 │           │       │
  │            │             │─search(emb)─────────────────────►│           │       │
  │            │             │                  │                 │─Query─────────────►│
  │            │             │                  │                 │◄─Results───────────│
  │            │             │◄─────chunks──────────────────────│           │       │
  │            │             │─buildPrompt()    │                 │           │       │
  │            │             │─callLLM()────────────────────────────────────►│       │
  │            │             │◄────response──────────────────────────────────│       │
  │            │◄─response───│                  │                 │           │       │
  │◄─JSON──────│             │                  │                 │           │       │
```

---

## 핵심 컴포넌트

### 1. DocumentService

**책임**: 문서 처리 파이프라인 오케스트레이션

```java
@Service
public class DocumentService {

    private final EmbeddingService embeddingService;
    private final TextChunker textChunker;
    private final DocumentChunkRepository repository;

    /**
     * 문서 처리 파이프라인
     * 1. 텍스트 추출 (Apache Tika)
     * 2. 청킹 (TextChunker)
     * 3. 임베딩 생성 (EmbeddingService)
     * 4. DB 저장 (Repository)
     */
    public DocumentUploadResponse processDocument(MultipartFile file) {
        // 구현 내용...
    }
}
```

**주요 메서드**:
- `processDocument()`: 전체 파이프라인 실행
- `extractText()`: Apache Tika로 텍스트 추출
- `validateFile()`: 파일 타입 및 크기 검증
- `generateDocumentId()`: 고유 ID 생성 (UUID)

### 2. EmbeddingService

**책임**: OpenAI API를 통한 임베딩 생성

```java
@Service
public class EmbeddingService {

    private final OpenAiService openAiService;

    /**
     * 단일 텍스트 임베딩 생성
     * @param text 임베딩할 텍스트
     * @return 1536차원 벡터 (List<Float>)
     */
    public List<Float> embed(String text) {
        // OpenAI API 호출
        // text-embedding-ada-002 모델 사용
    }

    /**
     * 배치 임베딩 생성
     * @param texts 임베딩할 텍스트 리스트 (최대 100개)
     * @return 임베딩 벡터 리스트
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        // 배치 처리로 API 호출 최소화
    }
}
```

**주요 기능**:
- OpenAI `text-embedding-ada-002` 모델 사용
- 배치 처리 (최대 100개)
- 재시도 로직 (Rate Limit 대응)
- 에러 핸들링

### 3. TextChunker

**책임**: 텍스트를 의미 단위로 청킹

```java
@Component
public class TextChunker {

    private final int chunkSize;      // 1000 토큰
    private final int chunkOverlap;   // 200 토큰

    /**
     * 재귀적 텍스트 청킹
     * 1. 단락 단위 분리 (\n\n)
     * 2. 문장 단위 분리 (.)
     * 3. 문자 단위 분리
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        // 1. 단락으로 분리
        String[] paragraphs = text.split("\n\n");

        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (countTokens(currentChunk + paragraph) > chunkSize) {
                // 현재 청크 저장
                chunks.add(currentChunk.toString());

                // 오버랩 적용
                currentChunk = new StringBuilder(
                    getLastNTokens(currentChunk, chunkOverlap)
                );
            }
            currentChunk.append(paragraph).append("\n\n");
        }

        return chunks;
    }
}
```

**청킹 전략**:
- **크기**: 1000 토큰 (~4000자)
- **오버랩**: 200 토큰 (~800자)
- **분할 순서**: 단락 → 문장 → 문자
- **메타데이터 보존**: 파일명, 청크 인덱스

### 4. VectorSearchService

**책임**: pgvector를 사용한 시맨틱 검색

```java
@Service
public class VectorSearchService {

    private final DocumentChunkRepository repository;

    /**
     * 코사인 유사도 기반 검색
     * @param queryEmbedding 질문 임베딩
     * @param topK 검색할 청크 수
     * @param threshold 최소 유사도
     * @return 유사도 높은 순으로 정렬된 청크
     */
    public List<SearchResult> search(
        List<Float> queryEmbedding,
        int topK,
        double threshold
    ) {
        // Native Query 실행
        String sql = """
            SELECT
                id,
                document_id,
                filename,
                chunk_index,
                content,
                1 - (embedding <=> :queryEmbedding) AS similarity
            FROM document_chunks
            WHERE 1 - (embedding <=> :queryEmbedding) > :threshold
            ORDER BY embedding <=> :queryEmbedding
            LIMIT :topK
            """;

        // 쿼리 실행 및 결과 매핑
    }
}
```

**검색 전략**:
- **유사도 함수**: 코사인 유사도 (`<=>` 연산자)
- **Top-K**: 3개 청크 (설정 가능)
- **Threshold**: 0.7 (70% 이상 유사도)
- **인덱스**: IVFFlat (Inverted File with Flat compression)

### 5. ChatService

**책임**: RAG 파이프라인 오케스트레이션

```java
@Service
public class ChatService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final OpenAiService openAiService;

    /**
     * RAG 기반 질의응답
     */
    public ChatResponse chat(ChatRequest request) {
        // 1. 질문 임베딩 생성
        List<Float> questionEmbedding =
            embeddingService.embed(request.getMessage());

        // 2. 벡터 검색
        List<SearchResult> results =
            vectorSearchService.search(questionEmbedding, 3, 0.7);

        // 3. 프롬프트 구성
        String prompt = buildPrompt(request.getMessage(), results);

        // 4. LLM 호출
        String response = callOpenAI(prompt);

        // 5. 응답 생성
        return ChatResponse.builder()
            .response(response)
            .sources(toSourceReferences(results))
            .build();
    }

    private String buildPrompt(String question, List<SearchResult> results) {
        StringBuilder prompt = new StringBuilder();

        // System prompt
        prompt.append("당신은 고객 지원 AI입니다. ");
        prompt.append("제공된 컨텍스트만 사용하여 답변하세요.\n\n");

        // Context
        prompt.append("[문서 컨텍스트]\n");
        for (SearchResult result : results) {
            prompt.append("파일: ").append(result.getFilename());
            prompt.append(" (청크 ").append(result.getChunkIndex()).append(")\n");
            prompt.append(result.getContent()).append("\n\n");
        }

        // Question
        prompt.append("[질문]\n");
        prompt.append(question);

        return prompt.toString();
    }
}
```

**프롬프트 구조**:
```
[System] 역할 정의 및 제약사항
[Context] 검색된 청크들
[Question] 사용자 질문
```

---

## 데이터베이스 설계

### ERD

```
┌─────────────────────────────────────────┐
│         document_chunks                 │
├─────────────────────────────────────────┤
│ PK │ id                 BIGSERIAL       │
│    │ document_id        VARCHAR(255)    │
│    │ filename           VARCHAR(255)    │
│    │ chunk_index        INTEGER         │
│    │ content            TEXT            │
│    │ embedding          vector(1536)    │
│    │ source_type        VARCHAR(50)     │
│    │ created_at         TIMESTAMP       │
└─────────────────────────────────────────┘

인덱스:
- idx_embedding: USING ivfflat (embedding vector_cosine_ops)
- idx_document_id: (document_id)
- idx_created_at: (created_at)
```

### 테이블 상세

#### document_chunks

| 컬럼 | 타입 | Null | 설명 |
|-----|------|------|------|
| id | BIGSERIAL | NO | 기본 키 (자동 증가) |
| document_id | VARCHAR(255) | NO | 문서 고유 ID (UUID) |
| filename | VARCHAR(255) | NO | 원본 파일명 |
| chunk_index | INTEGER | NO | 청크 순서 (0부터 시작) |
| content | TEXT | NO | 청크 텍스트 내용 |
| embedding | vector(1536) | YES | OpenAI 임베딩 벡터 |
| source_type | VARCHAR(50) | YES | 파일 타입 (txt, md 등) |
| created_at | TIMESTAMP | NO | 생성 시간 (기본값: now()) |

### 인덱스 전략

#### 1. IVFFlat 벡터 인덱스

```sql
CREATE INDEX idx_embedding
ON document_chunks
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

**설명**:
- **IVFFlat**: Inverted File with Flat compression
- **lists = 100**: 클러스터 수 (행 수의 제곱근 권장)
- **vector_cosine_ops**: 코사인 유사도 연산자

**성능**:
- 정확도: ~95% (approximate)
- 속도: O(log n) vs O(n) (선형 검색)

#### 2. 문서 ID 인덱스

```sql
CREATE INDEX idx_document_id ON document_chunks(document_id);
```

**용도**: 특정 문서의 모든 청크 조회

#### 3. 생성 시간 인덱스

```sql
CREATE INDEX idx_created_at ON document_chunks(created_at);
```

**용도**: 최신 문서 필터링, 시간 범위 검색

### 쿼리 예시

#### 벡터 유사도 검색

```sql
SELECT
    id,
    document_id,
    filename,
    chunk_index,
    content,
    1 - (embedding <=> '[0.1, 0.2, ..., 0.5]'::vector) AS similarity
FROM document_chunks
WHERE 1 - (embedding <=> '[0.1, 0.2, ..., 0.5]'::vector) > 0.7
ORDER BY embedding <=> '[0.1, 0.2, ..., 0.5]'::vector
LIMIT 3;
```

#### 문서별 청크 조회

```sql
SELECT chunk_index, left(content, 100) as preview
FROM document_chunks
WHERE document_id = 'abc-123-def'
ORDER BY chunk_index;
```

---

## API 설계

상세한 API 명세는 [docs/API.md](./docs/API.md)를 참조하세요.

### Endpoints Overview

| Method | Path | 설명 |
|--------|------|------|
| POST | /api/v1/documents/upload | 문서 업로드 |
| GET | /api/v1/documents | 문서 목록 조회 (향후) |
| DELETE | /api/v1/documents/{id} | 문서 삭제 (향후) |
| POST | /api/v1/chat | 챗봇 질의응답 |
| GET | /api/v1/health | 헬스체크 |

### 요청/응답 예시

#### POST /api/v1/chat

**Request:**
```json
{
  "message": "비밀번호를 어떻게 재설정하나요?"
}
```

**Response:**
```json
{
  "response": "비밀번호를 재설정하려면 다음 단계를 따르세요...",
  "sources": [
    {
      "documentId": "abc-123",
      "filename": "user-manual.txt",
      "chunkIndex": 5,
      "similarity": 0.89,
      "content": "비밀번호 재설정 방법: 설정 > 계정..."
    }
  ],
  "timestamp": "2024-01-20T10:30:00Z"
}
```

---

## 구현 전략

### Phase 1: 인프라 설정

**목표**: 개발 환경 구축

```bash
# 1. PostgreSQL + pgvector 실행
docker-compose up -d

# 2. 의존성 추가 (build.gradle)
# 3. application.yaml 설정
# 4. DB 스키마 생성
```

**검증**:
- PostgreSQL 연결 확인
- pgvector extension 활성화 확인
- Spring Boot 애플리케이션 시작 확인

### Phase 2: 문서 처리 파이프라인

**구현 순서**:

1. **Entity & Repository**
   ```java
   @Entity
   public class DocumentChunk {
       @Id @GeneratedValue
       private Long id;

       @Column(columnDefinition = "vector(1536)")
       private List<Float> embedding;

       // ...
   }
   ```

2. **TextChunker**
   - 단위 테스트 작성
   - 재귀적 청킹 로직 구현
   - 오버랩 처리

3. **EmbeddingService**
   - OpenAI 클라이언트 설정
   - 단일/배치 임베딩 메서드
   - 에러 핸들링

4. **DocumentService**
   - 파이프라인 오케스트레이션
   - 트랜잭션 관리

5. **DocumentController**
   - REST API 엔드포인트
   - 파일 업로드 처리

**테스트**:
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@test.txt"
```

### Phase 3: RAG 질의응답

**구현 순서**:

1. **VectorSearchService**
   - Native Query 작성
   - 결과 매핑

2. **PromptBuilder**
   - 템플릿 관리
   - 컨텍스트 포맷팅

3. **ChatService**
   - RAG 파이프라인
   - LLM 호출

4. **ChatController**
   - REST API 엔드포인트

**테스트**:
```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "질문"}'
```

### Phase 4: 통합 & 최적화

- E2E 테스트
- 로깅 추가
- 에러 핸들링 개선
- 성능 측정

---

## 설계 결정 사항

### 1. PostgreSQL + pgvector vs 전용 벡터 DB

**선택**: PostgreSQL + pgvector

**이유**:
- ✅ 기존 RDB 스킬 활용 가능
- ✅ 벡터 + 메타데이터를 함께 관리
- ✅ 트랜잭션 지원
- ✅ 소규모 프로젝트에 충분한 성능
- ❌ Pinecone, Weaviate보다 느림 (대규모)

**대안**: Chroma, Qdrant, Pinecone
- 전용 벡터 DB가 더 빠르지만 학습 목적으로는 PostgreSQL이 적합

### 2. 직접 구현 vs Langchain4j

**선택**: 직접 구현

**이유**:
- ✅ RAG 원리 깊이 이해
- ✅ 각 단계별 제어 가능
- ✅ 디버깅 용이
- ❌ 개발 시간 증가
- ❌ 베스트 프랙티스 미적용

**대안**: Langchain4j
- 빠른 프로토타입에는 유리하지만 학습 목적에는 부적합

### 3. 청킹 크기: 1000 토큰

**이유**:
- GPT-3.5-turbo 컨텍스트: 4096 토큰
- 3개 청크 + 질문 + 프롬프트 ≈ 3500 토큰
- 충분한 컨텍스트 + 여유 공간

**대안**:
- 500 토큰: 더 정확하지만 컨텍스트 부족
- 2000 토큰: 더 많은 정보지만 관련성 떨어짐

### 4. Top-K = 3

**이유**:
- 충분한 컨텍스트 제공
- LLM 컨텍스트 윈도우에 적합
- 노이즈 최소화

**대안**:
- K=5: 더 많은 정보지만 노이즈 증가
- K=1: 정확하지만 컨텍스트 부족

### 5. 임베딩 모델: text-embedding-ada-002

**이유**:
- 가격 대비 성능 우수
- 1536 차원 (적절한 크기)
- 안정적이고 검증됨

**대안**:
- text-embedding-3-small: 더 저렴하지만 성능 비슷
- text-embedding-3-large: 더 정확하지만 비쌈

### 6. 채팅 모델: GPT-3.5-turbo

**이유**:
- 빠른 응답 (1-2초)
- 저렴한 비용 (~$0.002/요청)
- 충분한 품질

**대안**:
- GPT-4: 더 정확하지만 20배 비쌈
- GPT-4-turbo: GPT-4보다 빠르고 저렴하지만 여전히 고가

---

## 성능 고려사항

### 응답 시간 목표

| 작업 | 목표 | 현실 |
|-----|------|------|
| 문서 업로드 | < 5초 | 3-10초 (파일 크기 의존) |
| 질의응답 | < 3초 | 2-4초 |
| 벡터 검색 | < 100ms | 50-200ms |

### 병목 지점

1. **OpenAI API 호출**: 1-2초
   - 해결: 배치 처리, 캐싱

2. **벡터 검색**: 50-200ms
   - 해결: IVFFlat 인덱스 최적화

3. **텍스트 추출**: 100-500ms
   - 해결: 비동기 처리 (향후)

### 확장성 전략

**현재 (MVP)**:
- 단일 서버
- 동기 처리
- 인메모리 캐시

**향후**:
- 로드 밸런서 + 다중 인스턴스
- 비동기 처리 (Queue)
- Redis 캐싱
- CDN (문서 파일)

---

## 보안 고려사항

### 1. API Key 관리

```yaml
# ❌ 절대 하지 말 것
rag:
  openai:
    api-key: sk-1234567890abcdef

# ✅ 환경 변수 사용
rag:
  openai:
    api-key: ${OPENAI_API_KEY}
```

### 2. 파일 업로드 검증

- 파일 크기 제한: 10MB
- 허용 확장자: txt, md
- MIME 타입 검증
- 바이러스 스캔 (향후)

### 3. SQL Injection 방지

- JPA Criteria API 사용
- Named Parameters
- 입력 검증

### 4. Rate Limiting

- API 엔드포인트별 제한 (향후)
- OpenAI API 호출 제한

---

## 모니터링 & 로깅

### 로깅 전략

```java
@Slf4j
@Service
public class ChatService {

    public ChatResponse chat(ChatRequest request) {
        log.info("Chat request received: {}", request.getMessage());

        // 1. 임베딩 생성
        long startEmbed = System.currentTimeMillis();
        List<Float> embedding = embeddingService.embed(request.getMessage());
        log.debug("Embedding generated in {}ms",
            System.currentTimeMillis() - startEmbed);

        // 2. 검색
        long startSearch = System.currentTimeMillis();
        List<SearchResult> results = vectorSearchService.search(embedding);
        log.debug("Search completed in {}ms, found {} chunks",
            System.currentTimeMillis() - startSearch, results.size());

        // 3. LLM 호출
        long startLLM = System.currentTimeMillis();
        String response = callOpenAI(prompt);
        log.info("LLM response generated in {}ms",
            System.currentTimeMillis() - startLLM);

        return buildResponse(response, results);
    }
}
```

### 메트릭

**추적할 메트릭**:
- OpenAI API 호출 횟수
- 평균 응답 시간
- 에러율
- 벡터 검색 성능
- 비용 (토큰 사용량)

---

## 참고 자료

- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [OpenAI Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)
- [RAG 논문](https://arxiv.org/abs/2005.11401)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)

---

**문서 버전**: 1.0
**최종 수정일**: 2024-01-20
