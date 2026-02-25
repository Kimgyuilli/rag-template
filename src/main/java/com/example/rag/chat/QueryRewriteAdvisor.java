package com.example.rag.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 사용자 질문을 벡터 검색에 최적화된 쿼리로 재작성하는 Advisor.
 * 재작성 쿼리는 context에만 저장하고, prompt의 메시지는 변경하지 않는다.
 * RetrievalRerankAdvisor가 context에서 재작성 쿼리를 읽어 벡터 검색에 사용한다.
 */
public class QueryRewriteAdvisor implements BaseAdvisor {

	private static final Logger log = LoggerFactory.getLogger(QueryRewriteAdvisor.class);

	/** RetrievalRerankAdvisor가 읽을 context 키 */
	static final String REWRITTEN_QUERY_KEY = "rewrittenQuery";

	private static final String REWRITE_PROMPT = """
			당신은 검색 쿼리 최적화 전문가입니다.
			사용자의 질문을 벡터 데이터베이스 검색에 적합한 형태로 재작성하세요.

			규칙:
			1. 핵심 키워드와 의미를 보존하세요.
			2. 구어체, 불필요한 조사, 감탄사 등을 제거하세요.
			3. 검색에 유리한 명확하고 간결한 문장으로 바꾸세요.
			4. 재작성된 쿼리만 출력하세요. 설명이나 부가 텍스트는 금지합니다.

			사용자 질문: %s
			""";

	private final ChatModel chatModel;
	private final int order;

	public QueryRewriteAdvisor(ChatModel chatModel, int order) {
		this.chatModel = chatModel;
		this.order = order;
	}

	@Override
	public int getOrder() {
		return order;
	}

	/**
	 * before: 사용자 질문을 검색용 쿼리로 재작성하여 context에 저장한다.
	 * prompt 메시지는 변경하지 않아 대화 이력이 보존된다.
	 */
	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		String originalQuery = request.prompt().getUserMessage().getText();

		String rewrittenQuery = chatModel.call(String.format(REWRITE_PROMPT, originalQuery)).trim();
		log.info("쿼리 리라이팅: '{}' → '{}'", originalQuery, rewrittenQuery);

		return request.mutate()
				.context(REWRITTEN_QUERY_KEY, rewrittenQuery)
				.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}
}
