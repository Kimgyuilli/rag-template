package com.example.rag.chat.advisor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import com.example.rag.chat.repository.KeywordSearchRepository;

/**
 * 하이브리드 검색(벡터 + 키워드) 후 RRF 병합 및 LLM 재순위화를 수행하는 Advisor.
 *
 * 1. 벡터 검색(top-10) + 키워드 검색(top-10) 병렬 실행
 * 2. RRF(Reciprocal Rank Fusion)로 결과 병합
 * 3. 상위 10개를 LLM 재순위화하여 최종 5개 선택
 */
public class RetrievalRerankAdvisor implements BaseAdvisor {

	private static final Logger log = LoggerFactory.getLogger(RetrievalRerankAdvisor.class);

	/** QuestionAnswerAdvisor 호환 — context에 검색 문서를 저장하는 키 */
	public static final String RETRIEVED_DOCUMENTS = "qa_retrieved_documents";
	/** QuestionAnswerAdvisor 호환 — 필터 표현식 파라미터 키 */
	public static final String FILTER_EXPRESSION = "qa_filter_expression";

	private static final int SEARCH_TOP_K = 10;
	private static final double SIMILARITY_THRESHOLD = 0.3;
	private static final int RERANK_TOP_N = 5;
	private static final int RRF_K = 60;

	private static final FilterExpressionTextParser FILTER_PARSER = new FilterExpressionTextParser();

	private static final String RERANK_PROMPT = """
			당신은 문서 관련성 평가 전문가입니다.
			사용자 질문과 검색된 문서 목록이 주어집니다.
			각 문서의 관련성을 평가하고, 가장 관련성 높은 문서의 번호를 순서대로 나열하세요.

			규칙:
			1. 관련성이 높은 순서대로 문서 번호만 쉼표로 구분하여 출력하세요.
			2. 관련 없는 문서는 제외하세요.
			3. 최대 %d개까지만 선택하세요.
			4. 숫자와 쉼표만 출력하세요. 설명은 금지입니다.

			사용자 질문: %s

			검색된 문서 목록:
			%s
			""";

	private static final String CONTEXT_TEMPLATE = """

			아래는 질문과 관련된 참고 문서입니다:
			---------------------
			%s
			---------------------
			""";

	private final VectorStore vectorStore;
	private final ChatModel chatModel;
	private final KeywordSearchRepository keywordSearchRepository;
	private final int order;

	public RetrievalRerankAdvisor(VectorStore vectorStore, ChatModel chatModel,
			KeywordSearchRepository keywordSearchRepository, int order) {
		this.vectorStore = vectorStore;
		this.chatModel = chatModel;
		this.keywordSearchRepository = keywordSearchRepository;
		this.order = order;
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		Map<String, Object> context = request.context();

		// QueryRewriteAdvisor가 재작성한 쿼리가 있으면 사용, 없으면 원본 질문 사용
		String query = context.containsKey(QueryRewriteAdvisor.REWRITTEN_QUERY_KEY)
				? context.get(QueryRewriteAdvisor.REWRITTEN_QUERY_KEY).toString()
				: request.prompt().getUserMessage().getText();

		// 카테고리 필터 추출
		String category = context.containsKey(FILTER_EXPRESSION)
				? context.get(FILTER_EXPRESSION).toString()
				: null;

		// 벡터 검색 수행
		SearchRequest.Builder searchBuilder = SearchRequest.builder()
				.query(query)
				.topK(SEARCH_TOP_K)
				.similarityThreshold(SIMILARITY_THRESHOLD);

		if (category != null) {
			searchBuilder.filterExpression(FILTER_PARSER.parse(category));
		}

		List<Document> vectorResults = vectorStore.similaritySearch(searchBuilder.build());
		log.info("벡터 검색 결과: {}개 문서", vectorResults.size());

		// 키워드 검색 수행
		List<Document> keywordResults = keywordSearchRepository.search(query, SEARCH_TOP_K, null);
		log.info("키워드 검색 결과: {}개 문서", keywordResults.size());

		// RRF 병합
		List<Document> candidates = mergeByRRF(vectorResults, keywordResults);
		log.info("RRF 병합 결과: {}개 문서", candidates.size());

		if (candidates.isEmpty()) {
			return request;
		}

		// 검색 결과가 RERANK_TOP_N 이하이면 재순위화 스킵 (불필요한 LLM 호출 방지)
		List<Document> selected;
		if (candidates.size() <= RERANK_TOP_N) {
			log.info("검색 결과 {}개 ≤ {} — 재순위화 스킵", candidates.size(), RERANK_TOP_N);
			selected = candidates;
		} else {
			selected = rerank(query, candidates);
			log.info("재순위화 후: {}개 문서 선택", selected.size());
		}

		String documentContext = selected.stream()
				.map(Document::getText)
				.collect(Collectors.joining("\n\n"));

		return request.mutate()
				.prompt(request.prompt().augmentSystemMessage(String.format(CONTEXT_TEMPLATE, documentContext)))
				.context(RETRIEVED_DOCUMENTS, selected)
				.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}

	/**
	 * RRF(Reciprocal Rank Fusion)로 벡터 검색과 키워드 검색 결과를 병합한다.
	 * score = Σ 1/(RRF_K + rank)
	 *
	 * @return RRF 점수 순으로 정렬된 상위 SEARCH_TOP_K개 문서
	 */
	private List<Document> mergeByRRF(List<Document> vectorResults, List<Document> keywordResults) {
		// Document ID → (RRF 점수, Document) 매핑
		Map<String, Double> scores = new HashMap<>();
		Map<String, Document> docMap = new LinkedHashMap<>();

		for (int i = 0; i < vectorResults.size(); i++) {
			Document doc = vectorResults.get(i);
			String id = doc.getId();
			scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
			docMap.putIfAbsent(id, doc);
		}

		for (int i = 0; i < keywordResults.size(); i++) {
			Document doc = keywordResults.get(i);
			String id = doc.getId();
			scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
			docMap.putIfAbsent(id, doc);
		}

		return scores.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(SEARCH_TOP_K)
				.map(e -> docMap.get(e.getKey()))
				.toList();
	}

	/**
	 * LLM으로 문서 관련성을 재평가하여 상위 N개를 반환한다.
	 */
	private List<Document> rerank(String query, List<Document> candidates) {
		StringBuilder docList = new StringBuilder();
		for (int i = 0; i < candidates.size(); i++) {
			docList.append(String.format("[%d] %s\n", i + 1,
					truncate(candidates.get(i).getText(), 200)));
		}

		String prompt = String.format(RERANK_PROMPT, RERANK_TOP_N, query, docList);

		try {
			String response = chatModel.call(prompt).trim();
			log.info("재순위화 LLM 응답: {}", response);

			return Arrays.stream(response.split("[,\\s]+"))
					.map(String::trim)
					.filter(s -> s.matches("\\d+"))
					.map(Integer::parseInt)
					.filter(idx -> idx >= 1 && idx <= candidates.size())
					.distinct()
					.limit(RERANK_TOP_N)
					.map(idx -> candidates.get(idx - 1))
					.toList();
		} catch (Exception e) {
			log.warn("재순위화 실패, 원본 순서 유지: {}", e.getMessage());
			return candidates.stream().limit(RERANK_TOP_N).toList();
		}
	}

	private static String truncate(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}
}
