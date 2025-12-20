# RAG 챗봇 시스템

> Spring Boot + PostgreSQL pgvector 기반 Retrieval-Augmented Generation (RAG) 챗봇

## 📌 프로젝트 개요

이 프로젝트는 **RAG(Retrieval-Augmented Generation)** 기술을 학습하기 위한 Spring Boot 기반 챗봇 시스템입니다. 추상화 라이브러리 없이 OpenAI API를 직접 사용하여 RAG의 핵심 원리를 이해하고 구현합니다.

### 주요 특징

- ✅ **원리 중심 학습**: Langchain4j 같은 추상화 없이 직접 구현
- ✅ **벡터 데이터베이스**: PostgreSQL + pgvector extension 사용
- ✅ **OpenAI API 통합**: 임베딩 생성 및 GPT 기반 응답 생성
- ✅ **문서 처리**: 텍스트 파일 업로드, 청킹, 벡터화
- ✅ **시맨틱 검색**: 코사인 유사도 기반 컨텍스트 검색
- ✅ **REST API**: 문서 업로드 및 챗봇 질의응답 엔드포인트

### 기술 스택

| 카테고리 | 기술 |
|---------|------|
| Backend | Spring Boot 4.0.1, Java 21 |
| Database | PostgreSQL 16 + pgvector |
| LLM | OpenAI API (GPT-3.5-turbo, text-embedding-ada-002) |
| 문서 처리 | Apache Tika |
| 빌드 도구 | Gradle 9.2.1 |

---

## 🚀 빠른 시작

### 사전 요구사항

- Java 21 이상
- Docker & Docker Compose
- OpenAI API Key ([여기서 발급](https://platform.openai.com/api-keys))

### 1. PostgreSQL + pgvector 실행

```bash
# Docker Compose로 PostgreSQL 시작
docker-compose up -d

# 연결 확인
docker exec -it rag-postgres psql -U postgres -d ragdb -c "SELECT version();"
```

### 2. 환경 변수 설정

프로젝트 루트에 `.env` 파일 생성:

```bash
OPENAI_API_KEY=your-openai-api-key-here
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

또는 환경 변수로 직접 설정:

```bash
export OPENAI_API_KEY="sk-..."
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"
```

### 3. 애플리케이션 실행

```bash
# Gradle로 빌드 및 실행
./gradlew bootRun

# 또는 JAR로 실행
./gradlew build
java -jar build/libs/rag-0.0.1-SNAPSHOT.jar
```

애플리케이션이 기본적으로 `http://localhost:8080`에서 실행됩니다.
개발 모드(`--spring.profiles.active=dev`)에서는 `http://localhost:8081`에서 실행됩니다.

---

## 📖 사용 방법

### 1. 문서 업로드

텍스트 파일을 업로드하여 지식 베이스에 추가:

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@user-manual.txt"
```

**응답 예시:**
```json
{
  "documentId": "abc123",
  "filename": "user-manual.txt",
  "status": "processed",
  "chunksCreated": 12,
  "message": "Document processed successfully"
}
```

### 2. 챗봇에 질문하기

업로드한 문서 내용을 기반으로 질문:

```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?"
  }'
```

**응답 예시:**
```json
{
  "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?",
  "answer": "RAG 시스템은 .txt와 .md 파일 형식을 지원합니다.",
  "sources": [
    {
      "documentId": "abc123",
      "filename": "test-document.txt",
      "chunkIndex": 1,
      "content": "지원 파일 형식: .txt, .md\n최대 파일 크기: 10MB",
      "similarity": 0.92,
      "sourceType": "txt"
    }
  ],
  "processingTimeMs": 1234,
  "model": "gpt-3.5-turbo",
  "timestamp": "2025-01-20T10:30:15"
}
```

**커스텀 파라미터 사용:**
```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "업로드 과정을 설명해주세요",
    "topK": 5,
    "similarityThreshold": 0.6
  }'
```

### 3. 헬스체크

```bash
curl http://localhost:8080/api/v1/health
```

---

## 🏗 아키텍처

### RAG 파이프라인

```
┌─────────────────────────────────────────────────────────────┐
│                    문서 처리 파이프라인                        │
└─────────────────────────────────────────────────────────────┘
  문서 업로드 → 텍스트 추출 → 청킹 → 임베딩 생성 → DB 저장

┌─────────────────────────────────────────────────────────────┐
│                    질의응답 파이프라인                         │
└─────────────────────────────────────────────────────────────┘
  질문 → 임베딩 생성 → 벡터 검색 → 컨텍스트 조립 → LLM 호출 → 답변
```

상세한 아키텍처는 [ARCHITECTURE.md](./docs/ARCHITECTURE.md)를 참조하세요.

### 주요 컴포넌트

| 컴포넌트 | 역할 |
|---------|------|
| `DocumentController` | 문서 업로드/삭제 REST API |
| `ChatController` | 채팅 질의응답 REST API |
| `DocumentService` | 문서 처리 파이프라인 오케스트레이션 |
| `ChatService` | RAG 파이프라인 오케스트레이션 및 LLM 호출 |
| `EmbeddingService` | OpenAI API로 임베딩 생성 (단일/배치) |
| `TextChunker` | 텍스트를 토큰 단위로 재귀적 청킹 |
| `VectorSearchService` | pgvector로 코사인 유사도 검색 |
| `PromptBuilder` | RAG 프롬프트 템플릿 생성 |
| `DocumentChunkRepository` | JPA Repository with pgvector 쿼리 |

---

## 📁 프로젝트 구조

```
rag/
├── src/main/java/com/example/rag/
│   ├── config/              # 설정 클래스 (OpenAI, DB, Properties)
│   ├── controller/          # REST API 컨트롤러
│   ├── service/             # 비즈니스 로직 (Embedding, Chat, Document 등)
│   ├── repository/          # JPA Repository
│   ├── model/
│   │   ├── entity/          # JPA Entity (DocumentChunk)
│   │   └── dto/             # DTO (Request/Response)
│   ├── exception/           # 예외 처리
│   └── util/                # 유틸리티 (TokenCounter 등)
├── src/main/resources/
│   ├── application.yaml     # 애플리케이션 설정
│   └── db/migration/        # DB 스키마 (Flyway, 선택)
├── docs/                    # 문서
│   ├── ARCHITECTURE.md      # 아키텍처 설계
│   ├── API.md               # API 명세
│   └── TESTING_GUIDE.md     # 테스트 가이드
├── docker-compose.yml       # PostgreSQL + pgvector
├── build.gradle             # Gradle 빌드 설정
└── README.md                # 이 파일
```

---

## 🔧 설정

주요 설정은 `src/main/resources/application.yaml`에서 관리합니다:

- **OpenAI 설정**: API 키, 모델 선택 (embedding, chat)
- **청킹 설정**: chunk-size (1000 토큰), chunk-overlap (200 토큰)
- **검색 설정**: top-k (3), similarity-threshold (0.7)
- **문서 설정**: allowed-extensions (txt, md)

상세 설정은 프로젝트의 `application.yaml` 파일을 참조하세요.

---

## 🧪 테스트

### 단위 테스트 실행

```bash
./gradlew test
```

### 통합 테스트

```bash
./gradlew integrationTest
```

### 수동 테스트 시나리오

1. **문서 업로드 테스트**
   ```bash
   echo "비밀번호 재설정: 설정 > 계정 > 비밀번호 변경" > test.txt
   curl -X POST http://localhost:8080/api/v1/documents/upload -F "file=@test.txt"
   ```

2. **질문 테스트**
   ```bash
   curl -X POST http://localhost:8081/api/v1/chat \
     -H "Content-Type: application/json" \
     -d '{"question": "비밀번호를 어떻게 바꾸나요?"}'
   ```

3. **데이터베이스 확인**
   - Docker를 통해 PostgreSQL에 접속하여 저장된 청크 확인 가능
   - pgAdmin 등의 GUI 도구 사용 권장

---

## 📊 데이터베이스 스키마

### document_chunks 테이블

| 컬럼 | 타입 | 설명 |
|-----|------|------|
| id | BIGSERIAL | 기본 키 |
| document_id | VARCHAR(255) | 문서 고유 ID |
| filename | VARCHAR(255) | 원본 파일명 |
| chunk_index | INTEGER | 청크 순서 |
| content | TEXT | 청크 텍스트 내용 |
| embedding | vector(1536) | OpenAI 임베딩 벡터 |
| source_type | VARCHAR(50) | 파일 타입 |
| created_at | TIMESTAMP | 생성 시간 |

**인덱스:**
- `idx_embedding`: IVFFlat 인덱스 (코사인 유사도 검색)
- `idx_document_id`: 문서 ID 인덱스

---

## 💰 비용 예상 (OpenAI API)

| 작업 | 모델 | 비용 |
|-----|------|------|
| 임베딩 생성 | text-embedding-ada-002 | ~$0.0001 / 1K tokens |
| 챗봇 응답 | gpt-3.5-turbo | ~$0.0015 / 1K tokens |

**일일 예상 비용 (테스트)**
- 문서 10개 업로드 (50K tokens): ~$0.005
- 질문 100회 (100K tokens): ~$0.15
- **총**: ~$0.16/day

**비용 절감 팁:**
- 임베딩 캐싱 (동일 텍스트 재임베딩 방지)
- gpt-3.5-turbo 사용 (gpt-4보다 20배 저렴)
- 청크 수 제한 (top-k=3)

---

## 🎓 학습 포인트

이 프로젝트를 통해 배울 수 있는 핵심 개념:

### 1. 임베딩 (Embedding)
- 텍스트를 1536차원 벡터로 변환
- 의미적 유사성을 수치화
- OpenAI의 `text-embedding-ada-002` 모델 사용

### 2. 벡터 검색 (Vector Search)
- 코사인 유사도 계산: `1 - (embedding <=> query)`
- pgvector의 IVFFlat 인덱싱
- Top-K 검색으로 가장 관련성 높은 청크 찾기

### 3. 청킹 (Chunking)
- 긴 문서를 500-1000 토큰 단위로 분할
- 200 토큰 오버랩으로 컨텍스트 보존
- 단락 → 문장 → 문자 순서로 재귀적 분할

### 4. 프롬프트 엔지니어링
- System/User role 구분
- 검색된 컨텍스트를 프롬프트에 주입
- Hallucination 방지 (컨텍스트만 사용하도록 지시)

### 5. RAG 파이프라인
- **Retrieval**: 벡터 검색으로 관련 청크 찾기
- **Augmentation**: 검색 결과를 프롬프트에 추가
- **Generation**: LLM이 컨텍스트 기반 답변 생성

---

## 🔜 향후 확장 계획

MVP 완성 후 추가할 수 있는 기능:

- [ ] **대화 히스토리**: 세션 기반 멀티턴 대화
- [ ] **다양한 파일 지원**: PDF, DOCX, JSON, 코드 파일
- [ ] **캐싱 레이어**: Redis로 임베딩 및 응답 캐싱
- [ ] **고급 검색**: Re-ranking, 하이브리드 검색 (키워드 + 시맨틱)
- [ ] **스트리밍 응답**: Server-Sent Events로 실시간 응답
- [ ] **메타데이터 필터링**: 특정 문서/날짜 범위 검색
- [ ] **비동기 처리**: 대용량 문서 배치 처리
- [ ] **모니터링**: 비용 추적, 성능 메트릭, 대시보드

---

## 🐛 트러블슈팅

### PostgreSQL 연결 실패

```bash
# PostgreSQL 컨테이너 상태 확인
docker ps

# 로그 확인
docker logs rag-postgres

# 재시작
docker-compose restart postgres
```

### pgvector extension 오류

- 컨테이너에 접속하여 extension 확인
- 필요시 pgvector extension 생성
- `docker logs rag-postgres`로 오류 확인

### OpenAI API 오류

- **401 Unauthorized**: API 키 확인
- **429 Rate Limit**: 요청 속도 제한, 잠시 후 재시도
- **500 Server Error**: OpenAI 서버 문제, [status.openai.com](https://status.openai.com) 확인

### 임베딩 저장 실패

- 벡터 차원이 1536인지 확인 (text-embedding-ada-002 기본 차원)
- 데이터베이스 스키마가 올바르게 생성되었는지 확인
- 애플리케이션 로그에서 상세 오류 메시지 확인

---

## 📚 참고 자료

### 공식 문서
- [OpenAI API Documentation](https://platform.openai.com/docs)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [Apache Tika Documentation](https://tika.apache.org/documentation.html)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)

### RAG 관련 논문 & 자료
- [Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks](https://arxiv.org/abs/2005.11401)
- [RAG 개념 설명 (OpenAI)](https://help.openai.com/en/articles/8868588-retrieval-augmented-generation-rag-and-semantic-search-for-gpts)

---

## 📝 라이선스

이 프로젝트는 학습 목적으로 만들어졌습니다.

---

## 🤝 기여

학습 프로젝트이지만 개선 제안은 환영합니다!

---

## 📧 문의

프로젝트 관련 질문이나 이슈는 GitHub Issues를 이용해주세요.

---

**Happy Learning! 🚀**
