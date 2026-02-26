# RAG 챗봇

Spring AI 기반 RAG(Retrieval-Augmented Generation) 챗봇.
문서를 업로드하면 청크 분할 → 벡터 임베딩 → 저장하고, 사용자 질문 시 관련 문서를 검색하여 LLM이 답변한다.

## 아키텍처

```
사용자 질문
    │
    ▼
┌─────────────────── Advisor Chain ───────────────────┐
│ 1. MessageChatMemoryAdvisor  — 대화 이력 주입       │
│ 2. QueryRewriteAdvisor       — 검색 쿼리 재작성     │
│ 3. RetrievalRerankAdvisor    — 하이브리드 검색+리랭킹│
└─────────────────────────────────────────────────────┘
    │
    ▼
  ChatModel (gpt-4o-mini) → 스트리밍 응답
```

### 검색 파이프라인 (RetrievalRerankAdvisor)

```
질문 → QueryRewrite(LLM) → ┬─ 벡터 검색 (pgvector, top-10)
                            └─ 키워드 검색 (tsvector, top-10)
                                      │
                                      ▼
                              RRF 병합 (top-10)
                                      │
                                      ▼
                            LLM 리랭킹 (top-5)
                                      │
                                      ▼
                             컨텍스트로 프롬프트에 주입
```

### 대화 메모리 (SummarizingChatMemory)

메시지가 20개를 초과하면 오래된 메시지를 LLM으로 요약하여 `[요약] + [최근 10개]`로 압축한다.
토큰 소비를 억제하면서 장기 대화의 맥락을 보존하는 방식.

### 청킹 전략 (StructuredTextChunker)

1. 마크다운 헤더 / 빈 줄 경계로 섹션 분리
2. 작은 섹션은 512 토큰 이내로 병합, 큰 섹션은 토큰 단위 재분할
3. 청크 간 64 토큰 overlap으로 문맥 끊김 방지
4. 토크나이저: `cl100k_base` (jtokkit)

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring AI 1.1 |
| Database | PostgreSQL 16 + pgvector (HNSW, cosine) |
| AI | OpenAI (gpt-4o-mini, text-embedding-3-small) |
| PDF 파싱 | Apache PDFBox 3.0 |
| Frontend | Vanilla HTML/CSS/JS |
| Infra | Docker Compose |

## 프로젝트 구조

```
src/main/java/com/example/rag/
├── chat/
│   ├── advisor/
│   │   ├── QueryRewriteAdvisor.java      # 구어체→검색 쿼리 재작성
│   │   └── RetrievalRerankAdvisor.java   # 하이브리드 검색 + RRF + LLM 리랭킹
│   ├── controller/ChatController.java     # SSE 스트리밍 채팅 API
│   ├── memory/SummarizingChatMemory.java  # 임계값 초과 시 LLM 요약
│   ├── repository/
│   │   ├── KeywordSearchRepository.java   # tsvector 키워드 검색
│   │   └── SessionRepository.java         # 세션 목록 조회
│   └── service/ChatService.java
├── config/
│   ├── AiConfig.java                      # ChatClient + Advisor 체인 구성
│   └── TsvectorInitializer.java           # tsvector 컬럼 자동 갱신
├── document/
│   ├── controller/DocumentController.java # 문서 CRUD + 파일 업로드 API
│   └── service/
│       ├── DocumentService.java           # 청크 분할 → 벡터 저장
│       ├── FileParserService.java         # PDF/TXT/MD 텍스트 추출
│       └── StructuredTextChunker.java     # 구조 기반 청킹 + overlap
└── RagApplication.java

src/main/resources/
├── static/          # 프론트엔드 (index.html, css/, js/)
├── application.yaml
└── schema.sql       # pgvector 인덱스, tsvector 설정
```

## 재사용 포인트

| 컴포넌트 | 재사용 시나리오 |
|----------|----------------|
| `StructuredTextChunker` | 마크다운/텍스트 문서를 토큰 기반으로 청크 분할할 때 |
| `RetrievalRerankAdvisor` | 벡터+키워드 하이브리드 검색 + RRF + LLM 리랭킹 파이프라인 |
| `QueryRewriteAdvisor` | 구어체/비정형 질문을 검색 최적화된 쿼리로 변환할 때 |
| `SummarizingChatMemory` | 장기 대화에서 토큰 절약하면서 맥락을 보존할 때 |
| `FileParserService` | PDF/TXT/MD 파일에서 텍스트 추출할 때 |
| `AiConfig` | Spring AI Advisor 체인 구성 패턴 참고용 |

## 예상 비용

> OpenAI 요금 기준 (2025.05): gpt-4o-mini input $0.15/1M, output $0.60/1M, text-embedding-3-small $0.02/1M

### 질문 1회당 API 호출 내역

| 호출 | 모델 | Input | Output | 비용 |
|------|------|------:|-------:|-----:|
| 쿼리 리라이팅 | gpt-4o-mini | ~200 | ~30 | $0.00005 |
| 벡터 검색 임베딩 | embedding-3-small | ~50 | - | $0.000001 |
| LLM 리랭킹 | gpt-4o-mini | ~800 | ~20 | $0.00013 |
| 최종 응답 생성 | gpt-4o-mini | ~2,500 | ~300 | $0.00056 |
| **합계** | | **~3,550** | **~350** | **~$0.0007** |

### 대화 단위 예상 비용

| 시나리오 | 비용 | 원화 환산 (₩1,400/$) |
|----------|-----:|-----:|
| 짧은 대화 (3턴) | ~$0.002 | ~₩3 |
| 일반 대화 (10턴) | ~$0.007 | ~₩10 |
| 긴 대화 (10턴 + 메모리 요약 1회) | ~$0.008 | ~₩11 |

- 리랭킹 대상이 5개 이하면 LLM 리랭킹을 스킵하므로 ~30% 절감
- 메모리 요약은 메시지 20개 초과 시에만 발생 (1회 ~$0.0004)

## 시작하기

### 사전 요구사항

- Java 21
- Docker & Docker Compose
- OpenAI API 키


