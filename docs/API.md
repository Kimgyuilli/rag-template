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
http://localhost:8080/api/v1
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
  "message": "비밀번호를 어떻게 재설정하나요?",
  "includeSourceContent": true,
  "maxSources": 3
}
```

**Parameters:**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|-----|------|------|--------|------|
| message | String | Yes | - | 사용자 질문 (최대 1000자) |
| includeSourceContent | Boolean | No | true | 소스 청크 내용 포함 여부 |
| maxSources | Integer | No | 3 | 반환할 최대 소스 수 (1-5) |

#### Response

**200 OK**

```json
{
  "response": "비밀번호를 재설정하려면 다음 단계를 따르세요:\n\n1. 로그인 페이지에서 '비밀번호 찾기'를 클릭합니다.\n2. 등록된 이메일 주소를 입력합니다.\n3. 받은 이메일의 재설정 링크를 클릭합니다.\n4. 새 비밀번호를 입력하고 확인합니다.",
  "sources": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "user-manual.txt",
      "chunkIndex": 5,
      "similarity": 0.89,
      "content": "비밀번호 재설정 방법:\n1. 로그인 페이지 접속\n2. '비밀번호 찾기' 클릭\n3. 이메일 주소 입력\n4. 재설정 링크 수신..."
    },
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "faq.md",
      "chunkIndex": 12,
      "similarity": 0.82,
      "content": "Q: 비밀번호를 잊어버렸어요.\nA: 비밀번호 찾기 기능을 이용하세요..."
    },
    {
      "documentId": "abc-123-def-456",
      "filename": "security-guide.txt",
      "chunkIndex": 3,
      "similarity": 0.75,
      "content": "보안을 위해 비밀번호는 최소 8자 이상이어야 하며..."
    }
  ],
  "metadata": {
    "searchDurationMs": 150,
    "llmDurationMs": 1850,
    "totalDurationMs": 2100,
    "tokensUsed": 450
  },
  "timestamp": "2024-01-20T10:30:15Z"
}
```

**includeSourceContent=false인 경우:**

```json
{
  "response": "비밀번호를 재설정하려면...",
  "sources": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "user-manual.txt",
      "chunkIndex": 5,
      "similarity": 0.89
    }
  ],
  "metadata": { ... },
  "timestamp": "2024-01-20T10:30:15Z"
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
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "비밀번호를 어떻게 재설정하나요?",
    "includeSourceContent": true,
    "maxSources": 3
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

### 5. 문서 삭제 (향후 구현)

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

```json
{
  "message": "문서가 성공적으로 삭제되었습니다.",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "deletedChunks": 12,
  "timestamp": "2024-01-20T10:30:00Z"
}
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

## DTOs (Data Transfer Objects)

### ChatRequest

```java
public class ChatRequest {
    @NotBlank(message = "질문은 필수입니다.")
    @Size(max = 1000, message = "질문은 1000자를 초과할 수 없습니다.")
    private String message;

    private Boolean includeSourceContent = true;

    @Min(1)
    @Max(5)
    private Integer maxSources = 3;
}
```

### ChatResponse

```java
public class ChatResponse {
    private String response;
    private List<SourceReference> sources;
    private ResponseMetadata metadata;
    private LocalDateTime timestamp;
}
```

### SourceReference

```java
public class SourceReference {
    private String documentId;
    private String filename;
    private Integer chunkIndex;
    private Double similarity;
    private String content;  // includeSourceContent=true일 때만
}
```

### DocumentUploadResponse

```java
public class DocumentUploadResponse {
    private String documentId;
    private String filename;
    private String status;
    private Integer chunksCreated;
    private Integer totalTokens;
    private String message;
    private LocalDateTime timestamp;
}
```

---

## 사용 시나리오

### 시나리오 1: 기본 사용 흐름

```bash
# 1. 헬스체크
curl http://localhost:8080/api/v1/health

# 2. 문서 업로드
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@manual.txt"

# 응답: {"documentId": "abc-123", "chunksCreated": 10, ...}

# 3. 질문하기
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "사용법을 알려주세요"}'

# 응답: {"response": "사용법은 다음과 같습니다...", "sources": [...]}
```

### 시나리오 2: 소스 내용 제외

```bash
# 응답 크기를 줄이고 싶을 때
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "비밀번호 재설정 방법은?",
    "includeSourceContent": false,
    "maxSources": 1
  }'
```

### 시나리오 3: 여러 문서 업로드 후 통합 검색

```bash
# 1. 첫 번째 문서 업로드
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@user-guide.txt"

# 2. 두 번째 문서 업로드
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@faq.md"

# 3. 세 번째 문서 업로드
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@troubleshooting.txt"

# 4. 질문 - 모든 문서에서 검색됨
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "로그인이 안돼요"}'

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

## 에러 처리 예시

### Java (Spring Boot)

```java
try {
    ChatResponse response = chatService.chat(request);
    return ResponseEntity.ok(response);
} catch (InvalidRequestException e) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
} catch (EmbeddingException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("EMBEDDING_FAILED", e.getMessage()));
} catch (Exception e) {
    log.error("Unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
}
```

### JavaScript (Fetch API)

```javascript
async function askQuestion(message) {
    try {
        const response = await fetch('http://localhost:8080/api/v1/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                message: message,
                includeSourceContent: true,
                maxSources: 3
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error.message);
        }

        const data = await response.json();
        console.log('응답:', data.response);
        console.log('출처:', data.sources);
        return data;
    } catch (error) {
        console.error('질문 실패:', error.message);
        throw error;
    }
}
```

### Python (requests)

```python
import requests

def ask_question(message):
    url = 'http://localhost:8080/api/v1/chat'
    payload = {
        'message': message,
        'includeSourceContent': True,
        'maxSources': 3
    }

    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()
        data = response.json()
        print(f"응답: {data['response']}")
        print(f"출처 수: {len(data['sources'])}")
        return data
    except requests.exceptions.HTTPError as e:
        error_data = e.response.json()
        print(f"오류: {error_data['error']['message']}")
        raise
```

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
