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
			사용자의 질문을 벡터 검색에 적합한 형태로 재작성하세요.

			규칙:
			1. 원본 질문의 핵심 단어를 반드시 포함하세요.
			2. 구어체, 줄임말, 감탄사를 자연스러운 문장으로 바꾸세요.
			3. 짧은 키워드가 아닌, 완전한 문장으로 작성하세요.
			4. 재작성된 쿼리만 출력하세요.

			예시:
			- "환불 ㄱㄴ?" → "환불은 언제까지 가능한가요?"
			- "탈퇴하면 내 정보 어떻게 됨?" → "회원 탈퇴 시 개인정보는 어떻게 처리되나요?"
			- "배송 얼마나 걸려?" → "배송은 얼마나 걸리나요?"

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
