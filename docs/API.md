# RAG 챗봇 시스템 API 명세

## 목차

1. [개요](#개요)
2. [인증](#인증)
3. [공통 응답 형식](#공통-응답-형식)
4. [에러 코드](#에러-코드)
5. [API Endpoints](#api-endpoints)

---

## 개요

### Base URL

```
http://localhost:8081/api/v1  # Development (dev profile)
http://localhost:8080/api/v1  # Production
```

### Content Type

모든 요청과 응답은 `application/json` 형식을 사용합니다. (파일 업로드 제외)

### 버전

현재 API 버전: `v1`

---

## 인증

현재 MVP 버전에서는 인증이 구현되어 있지 않습니다.

**향후 계획:**
- API Key 기반 인증
- JWT 토큰 인증
- Rate Limiting

---

## 공통 응답 형식

### 성공 응답

```json
{
  "status": "success",
  "data": { ... },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

### 에러 응답

```json
{
  "status": "error",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "파일 타입이 지원되지 않습니다.",
    "details": "허용된 확장자: txt, md"
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

---

## 에러 코드

| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| `INVALID_REQUEST` | 400 | 잘못된 요청 (유효성 검증 실패) |
| `FILE_TOO_LARGE` | 400 | 파일 크기 초과 (10MB 제한) |
| `UNSUPPORTED_FILE_TYPE` | 400 | 지원되지 않는 파일 타입 |
| `DOCUMENT_NOT_FOUND` | 404 | 문서를 찾을 수 없음 |
| `EMBEDDING_FAILED` | 500 | 임베딩 생성 실패 (OpenAI API 오류) |
| `VECTOR_SEARCH_FAILED` | 500 | 벡터 검색 실패 |
| `LLM_ERROR` | 500 | LLM 호출 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 내부 서버 오류 |

---

## API Endpoints

### 1. 헬스체크

시스템 상태를 확인합니다.

#### Request

```http
GET /api/v1/health
```

#### Response

**200 OK**

```json
{
  "status": "UP",
  "components": {
    "database": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "SELECT 1"
      }
    },
    "openai": {
      "status": "UP",
      "details": {
        "api": "available"
      }
    }
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

#### cURL Example

```bash
curl -X GET http://localhost:8080/api/v1/health
```

---

### 2. 문서 업로드

텍스트 파일을 업로드하고 벡터화하여 저장합니다.

#### Request

```http
POST /api/v1/documents/upload
Content-Type: multipart/form-data
```

**Parameters:**

| 이름 | 타입 | 필수 | 설명 |
|-----|------|------|------|
| file | File | Yes | 업로드할 파일 (.txt, .md) |

**제약사항:**
- 최대 파일 크기: 10MB
- 허용 확장자: txt, md
- UTF-8 인코딩 권장

#### Response

**200 OK**

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "user-manual.txt",
  "status": "processed",
  "chunksCreated": 12,
  "totalTokens": 8500,
  "message": "문서가 성공적으로 처리되었습니다.",
  "timestamp": "2024-01-20T10:30:00Z"
}
```

**400 Bad Request**

```json
{
  "status": "error",
  "error": {
    "code": "UNSUPPORTED_FILE_TYPE",
    "message": "지원되지 않는 파일 형식입니다.",
    "details": "허용된 확장자: txt, md"
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

**500 Internal Server Error**

```json
{
  "status": "error",
  "error": {
    "code": "EMBEDDING_FAILED",
    "message": "임베딩 생성 중 오류가 발생했습니다.",
    "details": "OpenAI API rate limit exceeded"
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

#### cURL Example

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@user-manual.txt"
```

#### 처리 과정

```
파일 업로드
    ↓
파일 검증 (크기, 타입)
    ↓
텍스트 추출 (Apache Tika)
    ↓
청킹 (1000 토큰, 200 오버랩)
    ↓
임베딩 생성 (OpenAI API)
    ↓
DB 저장 (PostgreSQL + pgvector)
    ↓
응답 반환
```

---

### 3. 챗봇 질의응답

업로드된 문서를 기반으로 질문에 답변합니다.

#### Request

```http
POST /api/v1/chat
Content-Type: application/json
```

**Request Body:**

```json
{
  "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?",
  "documentId": null,
  "topK": 3,
  "similarityThreshold": 0.7
}
```

**Parameters:**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|-----|------|------|--------|------|
| question | String | Yes | - | 사용자 질문 |
| documentId | String | No | null | 특정 문서 내에서만 검색 (null = 전체 검색) |
| topK | Integer | No | 3 | 검색할 최대 청크 수 |
| similarityThreshold | Double | No | 0.7 | 유사도 임계값 (0-1) |

#### Response

**200 OK**

```json
{
  "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?",
  "answer": "RAG 시스템은 .txt와 .md 파일 형식을 지원합니다. 최대 파일 크기는 10MB입니다.",
  "sources": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "rag-guide.txt",
      "chunkIndex": 1,
      "content": "지원 파일 형식: .txt, .md\n최대 파일 크기: 10MB",
      "similarity": 0.92,
      "sourceType": "txt"
    },
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "rag-guide.txt",
      "chunkIndex": 2,
      "content": "API 엔드포인트: POST /api/v1/documents/upload\n파일을 선택하여 업로드하면 자동으로 처리됩니다.",
      "similarity": 0.85,
      "sourceType": "txt"
    }
  ],
  "processingTimeMs": 1234,
  "model": "gpt-3.5-turbo",
  "timestamp": "2025-01-20T10:30:15Z"
}
```

**400 Bad Request**

```json
{
  "status": "error",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "질문이 비어있습니다.",
    "details": "message 필드는 필수입니다."
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

**404 Not Found** (문서가 하나도 없을 때)

```json
{
  "status": "error",
  "error": {
    "code": "NO_DOCUMENTS",
    "message": "검색 가능한 문서가 없습니다.",
    "details": "먼저 문서를 업로드해주세요."
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

**500 Internal Server Error**

```json
{
  "status": "error",
  "error": {
    "code": "LLM_ERROR",
    "message": "답변 생성 중 오류가 발생했습니다.",
    "details": "OpenAI API timeout"
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

#### cURL Example

```bash
# 기본 질문
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "RAG 시스템에서 지원하는 파일 형식은 무엇인가요?"
  }'

# 파라미터 지정
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "업로드 방법을 알려주세요",
    "topK": 5,
    "similarityThreshold": 0.6
  }'
```

#### 처리 과정

```
질문 수신
    ↓
질문 임베딩 생성 (OpenAI API)
    ↓
벡터 검색 (PostgreSQL pgvector)
  - Top-K=3
  - Similarity > 0.7
    ↓
프롬프트 구성
  - System: 역할 정의
  - Context: 검색된 청크
  - Question: 사용자 질문
    ↓
LLM 호출 (OpenAI GPT-3.5-turbo)
    ↓
응답 + 소스 정보 반환
```

---

### 4. 문서 목록 조회 (향후 구현)

업로드된 모든 문서를 조회합니다.

#### Request

```http
GET /api/v1/documents?page=0&size=10&sort=createdAt,desc
```

**Query Parameters:**

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|-----|------|------|--------|------|
| page | Integer | No | 0 | 페이지 번호 (0부터 시작) |
| size | Integer | No | 10 | 페이지 크기 (1-100) |
| sort | String | No | createdAt,desc | 정렬 기준 |

#### Response

**200 OK**

```json
{
  "documents": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "user-manual.txt",
      "chunksCount": 12,
      "totalTokens": 8500,
      "sourceType": "txt",
      "createdAt": "2024-01-20T10:30:00Z"
    },
    {
      "documentId": "abc-123-def-456",
      "filename": "faq.md",
      "chunksCount": 8,
      "totalTokens": 5200,
      "sourceType": "md",
      "createdAt": "2024-01-19T15:20:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

#### cURL Example

```bash
curl -X GET "http://localhost:8080/api/v1/documents?page=0&size=10"
```

---

### 5. 문서 삭제

특정 문서와 관련된 모든 청크를 삭제합니다.

#### Request

```http
DELETE /api/v1/documents/{documentId}
```

**Path Parameters:**

| 이름 | 타입 | 필수 | 설명 |
|-----|------|------|------|
| documentId | String | Yes | 삭제할 문서 ID (UUID) |

#### Response

**200 OK**

```
Document deleted successfully
```

**404 Not Found**

```json
{
  "status": "error",
  "error": {
    "code": "DOCUMENT_NOT_FOUND",
    "message": "문서를 찾을 수 없습니다.",
    "details": "documentId: 550e8400-e29b-41d4-a716-446655440000"
  },
  "timestamp": "2024-01-20T10:30:00Z"
}
```

#### cURL Example

```bash
curl -X DELETE "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000"
```

---

## 사용 시나리오

### 시나리오 1: 기본 사용 흐름

```bash
# 1. 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@manual.txt"

# 응답: {"documentId": "abc-123", "chunksCreated": 10, ...}

# 2. 질문하기
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "사용법을 알려주세요"}'

# 응답: {"answer": "사용법은 다음과 같습니다...", "sources": [...]}
```

### 시나리오 2: 커스텀 파라미터 사용

```bash
# 더 많은 결과를 원할 때
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "업로드 과정을 설명해주세요",
    "topK": 5,
    "similarityThreshold": 0.6
  }'
```

### 시나리오 3: 여러 문서 업로드 후 통합 검색

```bash
# 1. 첫 번째 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@user-guide.txt"

# 2. 두 번째 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@faq.md"

# 3. 세 번째 문서 업로드
curl -X POST http://localhost:8081/api/v1/documents/upload \
  -F "file=@troubleshooting.txt"

# 4. 질문 - 모든 문서에서 검색됨
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "로그인이 안돼요"}'

# 응답에 여러 문서의 관련 정보가 포함됨
```

---

## 성능 고려사항

### 응답 시간

| API | 예상 시간 | 영향 요인 |
|-----|----------|---------|
| POST /documents/upload | 3-10초 | 파일 크기, 청크 수, OpenAI API 속도 |
| POST /chat | 2-4초 | 벡터 검색 (50-200ms) + LLM (1-3초) |
| GET /health | < 100ms | DB 연결 확인 |

### Rate Limiting (향후)

```
- 문서 업로드: 10 requests/hour per IP
- 챗봇 질문: 100 requests/hour per IP
```

### 최적화 팁

1. **배치 업로드**: 여러 문서를 한 번에 업로드하지 말고 순차적으로
2. **소스 내용 제외**: 응답 크기 감소를 위해 `includeSourceContent=false`
3. **maxSources 조절**: 필요한 만큼만 요청
4. **캐싱 활용**: 동일한 질문은 캐싱된 응답 사용 (향후)

---

## 버전 히스토리

### v1.0 (MVP)

**출시일**: 2024-01-20

**기능**:
- ✅ 문서 업로드 (txt, md)
- ✅ 챗봇 질의응답
- ✅ 헬스체크

**제약사항**:
- 인증 없음
- 문서 목록/삭제 미구현
- 대화 히스토리 미지원
- PDF, DOCX 미지원

### v1.1 (계획)

**예정일**: TBD

**신규 기능**:
- [ ] 문서 목록 조회
- [ ] 문서 삭제
- [ ] PDF 파일 지원
- [ ] API Key 인증

### v2.0 (계획)

**예정일**: TBD

**신규 기능**:
- [ ] 대화 히스토리 (세션)
- [ ] 스트리밍 응답
- [ ] 메타데이터 필터링
- [ ] Rate Limiting

---

## 참고 자료

- [OpenAPI/Swagger 명세](./openapi.yaml) (향후 추가)
- [Postman Collection](./postman_collection.json) (향후 추가)
- [API 변경 로그](./CHANGELOG.md) (향후 추가)

---

**문서 버전**: 1.0
**최종 수정일**: 2024-01-20
**유지보수자**: RAG 팀
