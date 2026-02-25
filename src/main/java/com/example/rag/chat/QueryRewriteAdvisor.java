package com.example.rag.chat;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 사용자 질문을 벡터 검색에 최적화된 쿼리로 재작성하는 Advisor.
 * QuestionAnswerAdvisor(또는 RetrievalRerankAdvisor)보다 먼저 실행되어야 한다.
 */
public class QueryRewriteAdvisor implements BaseAdvisor {

	private static final Logger log = LoggerFactory.getLogger(QueryRewriteAdvisor.class);

	private static final String ORIGINAL_QUERY_KEY = "originalQuery";

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
	 * before: 원본 질문을 보존하고, 재작성된 쿼리로 user message를 교체한다.
	 */
	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		String originalQuery = request.prompt().getUserMessage().getText();

		String rewrittenQuery = chatModel.call(String.format(REWRITE_PROMPT, originalQuery)).trim();
		log.info("쿼리 리라이팅: '{}' → '{}'", originalQuery, rewrittenQuery);

		// user message를 재작성 쿼리로 교체
		List<Message> messages = new ArrayList<>();
		for (Message msg : request.prompt().getInstructions()) {
			if (msg.getMessageType() == MessageType.USER) {
				messages.add(new UserMessage(rewrittenQuery));
			} else {
				messages.add(msg);
			}
		}

		Prompt newPrompt = Prompt.builder()
				.messages(messages)
				.chatOptions(request.prompt().getOptions())
				.build();

		return request.mutate()
				.prompt(newPrompt)
				.context(ORIGINAL_QUERY_KEY, originalQuery)
				.build();
	}

	/**
	 * after: 원본 질문을 복원하여 최종 LLM이 원래 질문에 대해 응답하도록 한다.
	 */
	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}
}
