package com.example.rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/**
 * 채팅 서비스.
 * {@link ChatClient}를 통해 LLM에 질문을 전달하고 응답을 반환한다.
 * conversationId로 세션별 대화 이력을 구분한다.
 */
@Service
public class ChatService {

	private final ChatClient chatClient;

	public ChatService(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	/**
	 * 동기 방식으로 질문에 대한 답변을 반환한다.
	 *
	 * @param question       사용자 질문
	 * @param conversationId 대화 세션 식별자
	 * @param category       문서 카테고리 필터 (선택)
	 * @return LLM 응답 텍스트
	 */
	public String ask(String question, String conversationId, String category) {
		return chatClient.prompt()
				.user(question)
				.advisors(a -> {
					a.param(ChatMemory.CONVERSATION_ID, conversationId);
					if (category != null && !category.isBlank()) {
						a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "category == '" + category + "'");
					}
				})
				.call()
				.content();
	}

	/**
	 * 스트리밍 방식으로 질문에 대한 답변을 토큰 단위로 반환한다.
	 *
	 * @param question       사용자 질문
	 * @param conversationId 대화 세션 식별자
	 * @param category       문서 카테고리 필터 (선택)
	 * @return 토큰 단위 응답 스트림
	 */
	public Flux<String> askStream(String question, String conversationId, String category) {
		return chatClient.prompt()
				.user(question)
				.advisors(a -> {
					a.param(ChatMemory.CONVERSATION_ID, conversationId);
					if (category != null && !category.isBlank()) {
						a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "category == '" + category + "'");
					}
				})
				.stream()
				.content();
	}
}
