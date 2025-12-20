# RAG 시스템 테스트 가이드

이 가이드는 RAG 시스템의 전체 기능을 테스트하는 방법을 단계별로 설명합니다.

---

## 목차

1. [사전 준비](#사전-준비)
2. [환경 설정 테스트](#환경-설정-테스트)
3. [문서 업로드 테스트](#문서-업로드-테스트)
4. [RAG 질의응답 테스트](#rag-질의응답-테스트)
5. [문서 삭제 테스트](#문서-삭제-테스트)
6. [통합 테스트 실행](#통합-테스트-실행)
7. [문제 해결](#문제-해결)

---

## 사전 준비

### 1. 필수 요구사항 확인

```bash
# Java 버전 확인 (21 이상)
java -version

# Docker 확인
docker --version
docker-compose --version

# OpenAI API 키 준비
# https://platform.openai.com/api-keys 에서 발급
```

### 2. 환경 변수 설정

`.env` 파일 또는 환경 변수로 설정:

```bash
export OPENAI_API_KEY="sk-your-api-key-here"
```

**중요**: 테스트에는 실제 OpenAI API가 호출되므로 비용이 발생할 수 있습니다.
- 임베딩: ~$0.0001 / 1K tokens
- 채팅: ~$0.0015 / 1K tokens (gpt-3.5-turbo)

---

## 환경 설정 테스트

### Step 1: PostgreSQL + pgvector 시작

```bash
# Docker Compose로 PostgreSQL 시작
docker-compose up -d

# 컨테이너 상태 확인
docker ps | grep rag-postgres

# 예상 출력:
# rag-postgres ... Up ... 0.0.0.0:5432->5432/tcp
```

### Step 2: pgvector Extension 확인

```bash
# PostgreSQL 접속하여 extension 확인
docker exec -it rag-postgres psql -U postgres -d ragdb -c "\dx"

# pgvector extension이 설치되어 있는지 확인
```

### Step 3: 테이블 확인

```bash
# document_chunks 테이블 스키마 확인
docker exec -it rag-postgres psql -U postgres -d ragdb -c "\d document_chunks"

# 필요한 컬럼들이 존재하는지 확인
```

### Step 4: 애플리케이션 빌드

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 예상 출력:
# BUILD SUCCESSFUL in 10s
```

### Step 5: 애플리케이션 실행

```bash
# 개발 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는
java -jar build/libs/rag-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 예상 로그:
# Started RagApplication in 5.123 seconds (process running for 5.456)
# Tomcat started on port 8081 (http) with context path ''
```

---

## 문서 업로드 테스트

### Test Case 1: 기본 문서 업로드

```bash
# 테스트 파일 생성
cat > test-rag-guide.txt << 'EOF'
RAG 시스템 사용 가이드

1. 개요
RAG(Retrieval-Augmented Generation)는 검색과 생성을 결합한 AI 시스템입니다.

2. 문서 업로드 방법
- 지원 파일 형식: .txt, .md
- 최대 파일 크기: 10MB
- API 엔드포인트: POST /api/v1/documents/upload

3. 처리 과정
업로드된 문서는 다음 단계로 처리됩니다:
1) 텍스트 추출
2) 청킹 (1000 토큰 단위, 200 토큰 오버랩)
3) 임베딩 생성 (OpenAI text-embedding-ada-002)
4) 벡터 데이터베이스 저장 (PostgreSQL + pgvector)

4. 질문하기
문서가 업로드되면 관련 질문을 할 수 있습니다.
시스템이 자동으로 관련 정보를 검색하여 답변을 생성합니다.
EOF

# 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@test-rag-guide.txt"
```

**예상 응답:**
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "test-rag-guide.txt",
  "status": "processed",
  "chunksCreated": 1,
  "totalTokens": 150,
  "message": "Document processed successfully",
  "timestamp": "2025-01-20T10:30:00"
}
```

**검증:**
- `status`가 "processed"인지 확인
- `chunksCreated`가 1 이상인지 확인
- `documentId`가 UUID 형식인지 확인

### Test Case 2: 잘못된 파일 형식

```bash
# .exe 파일 업로드 시도 (실패해야 함)
echo "test" > test.exe
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@test.exe"
```

**예상 응답:**
```json
{
  "filename": "test.exe",
  "status": "failed",
  "message": "File type not allowed. Allowed extensions: txt,md"
}
```

### Test Case 3: 데이터베이스 확인

PostgreSQL 컨테이너에 접속하여 업로드된 청크가 저장되었는지 확인:

- document_chunks 테이블에 데이터가 삽입되었는지 확인
- filename, chunk_index, content 등의 필드 확인
- pgAdmin이나 DBeaver 같은 GUI 도구 사용 권장

---

## RAG 질의응답 테스트

### Test Case 4: 기본 질의응답

```bash
# 업로드한 문서 내용에 대한 질문
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?"
  }'
```

**예상 응답:**
```json
{
  "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?",
  "answer": "RAG 시스템은 .txt와 .md 파일 형식을 지원합니다.",
  "sources": [
    {
      "documentId": "550e8400-...",
      "filename": "test-rag-guide.txt",
      "chunkIndex": 0,
      "content": "지원 파일 형식: .txt, .md\n최대 파일 크기: 10MB",
      "similarity": 0.85,
      "sourceType": "txt"
    }
  ],
  "processingTimeMs": 1500,
  "model": "gpt-3.5-turbo",
  "timestamp": "2025-01-20T10:35:00"
}
```

**검증:**
- `answer`가 비어있지 않은지 확인
- `sources` 배열에 관련 청크가 포함되어 있는지 확인
- `similarity` 점수가 0.7 이상인지 확인
- `processingTimeMs`가 합리적인 범위(500-5000ms)인지 확인

### Test Case 5: 커스텀 파라미터

```bash
# topK와 similarityThreshold 조정
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "문서 처리 과정을 설명해주세요",
    "topK": 5,
    "similarityThreshold": 0.6
  }'
```

**검증:**
- `sources` 배열의 길이가 최대 5개인지 확인
- 모든 소스의 `similarity`가 0.6 이상인지 확인

### Test Case 6: 관련 없는 질문

```bash
# 문서에 없는 내용에 대한 질문
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "양자 컴퓨팅이 뭔가요?"
  }'
```

**예상 응답:**
- `sources`가 비어있거나 similarity가 매우 낮음 (< 0.3)
- `answer`가 "관련 정보를 찾을 수 없습니다" 같은 내용

### Test Case 7: 빈 질문 (검증 실패)

```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": ""
  }'
```

**예상 응답:**
- HTTP 400 Bad Request
- 에러 메시지 포함

---

## 문서 삭제 테스트

### Test Case 8: 문서 삭제

```bash
# 먼저 documentId 확인
DOCUMENT_ID="550e8400-e29b-41d4-a716-446655440000"

# 문서 삭제
curl -X DELETE "http://localhost:8081/api/v1/documents/${DOCUMENT_ID}"

# 예상 응답:
# Document deleted successfully
```

### Test Case 9: 삭제 확인

데이터베이스에서 해당 문서의 청크가 모두 삭제되었는지 확인:

- document_chunks 테이블에서 해당 document_id의 레코드가 0개인지 확인
- pgAdmin 등의 GUI 도구로 확인 가능

---

## 통합 테스트 실행

### 자동화된 통합 테스트

```bash
# 통합 테스트 실행 (OpenAI API 키 필요)
export OPENAI_API_KEY="sk-your-api-key"
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "DocumentUploadIntegrationTest"
./gradlew test --tests "ChatIntegrationTest"
```

### 전체 E2E 시나리오

```bash
# 1. 여러 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@doc1.txt"

curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@doc2.md"

# 2. 통합 검색 질문
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "전체 시스템 개요를 설명해주세요"}'

# 3. 결과 확인 - 여러 문서의 정보가 통합되어 답변됨
```

---

## 문제 해결

### 문제 1: "Connection refused" 오류

```bash
# PostgreSQL 상태 확인
docker ps | grep rag-postgres

# 재시작
docker-compose restart postgres

# 로그 확인
docker logs rag-postgres
```

### 문제 2: "OpenAI API Key not found"

```bash
# 환경 변수 확인
echo $OPENAI_API_KEY

# 없으면 설정
export OPENAI_API_KEY="sk-..."

# 애플리케이션 재시작
```

### 문제 3: "Embedding dimension mismatch"

**원인**: 벡터 차원이 맞지 않음

**해결**:
- text-embedding-ada-002의 기본 차원은 1536
- 데이터베이스 스키마의 vector(1536) 설정 확인
- 애플리케이션 로그에서 임베딩 생성 과정 확인

### 문제 4: "No similar documents found"

**원인**: 임계값(threshold)이 너무 높거나 관련 문서가 없음

**해결**:
```bash
# similarityThreshold 낮춰서 재시도
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "...",
    "similarityThreshold": 0.5
  }'
```

### 문제 5: 테스트가 너무 느림

**원인**: OpenAI API 호출 지연

**확인**:
```bash
# 처리 시간 확인
# processingTimeMs가 5000ms 이상이면 네트워크 또는 API 문제

# OpenAI 상태 확인
curl https://status.openai.com/api/v2/status.json
```

---

## 성능 벤치마크

### 예상 처리 시간

| 작업 | 예상 시간 | 주요 영향 요인 |
|-----|----------|--------------|
| 문서 업로드 (1KB) | 2-5초 | 임베딩 생성 |
| 문서 업로드 (10KB) | 5-15초 | 청크 수, 임베딩 배치 |
| 질의응답 | 1-3초 | 벡터 검색 + LLM 호출 |

### 벤치마크 테스트

```bash
# 시간 측정
time curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "테스트 질문"}'

# 예상: real 0m2.500s
```

---

## 체크리스트

전체 테스트 완료 체크리스트:

- [ ] PostgreSQL + pgvector 실행 확인
- [ ] 애플리케이션 빌드 성공
- [ ] 애플리케이션 실행 (포트 8081)
- [ ] 문서 업로드 성공 (정상 파일)
- [ ] 문서 업로드 실패 (잘못된 형식)
- [ ] 데이터베이스에 청크 저장 확인
- [ ] 질의응답 성공 (관련 질문)
- [ ] 소스 참조 포함 확인
- [ ] 커스텀 파라미터 동작 확인
- [ ] 관련 없는 질문 처리 확인
- [ ] 빈 질문 검증 확인
- [ ] 문서 삭제 성공
- [ ] 삭제 후 데이터베이스 확인
- [ ] 통합 테스트 실행 성공

---

## 추가 테스트 시나리오

### 대용량 문서 테스트

```bash
# 10KB 이상 문서 생성
python3 -c "print('RAG 테스트 내용\n' * 1000)" > large-doc.txt

# 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@large-doc.txt"

# chunksCreated 확인 (10개 이상 예상)
```

### 동시 요청 테스트

```bash
# 5개의 동시 요청
for i in {1..5}; do
  curl -X POST http://localhost:8081/api/v1/chat \
    -H "Content-Type: application/json" \
    -d '{"question": "테스트 질문 '${i}'"}' &
done
wait

# 모든 요청이 성공적으로 처리되는지 확인
```

---

**테스트 완료 후**: 모든 체크리스트 항목이 완료되면 RAG 시스템이 정상 작동하는 것입니다!
