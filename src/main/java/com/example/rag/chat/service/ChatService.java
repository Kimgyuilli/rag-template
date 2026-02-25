package com.example.rag.chat.service;

import java.util.List;
import java.util.function.Consumer;

import static com.example.rag.chat.advisor.RetrievalRerankAdvisor.FILTER_EXPRESSION;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.AdvisorSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 채팅 서비스.
 * {@link ChatClient}를 통해 LLM에 질문을 전달하고 응답을 반환한다.
 * conversationId로 세션별 대화 이력을 구분한다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

	private final ChatClient chatClient;
	private final ChatMemory chatMemory;

	/**
	 * 동기 방식으로 질문에 대한 답변을 반환한다.
	 */
	public String ask(String question, String conversationId, String category) {
		return chatClient.prompt()
				.user(question)
				.advisors(advisorParams(conversationId, category))
				.call()
				.content();
	}

	/**
	 * 스트리밍 방식으로 질문에 대한 답변을 토큰 단위로 반환한다.
	 */
	public Flux<String> askStream(String question, String conversationId, String category) {
		return chatClient.prompt()
				.user(question)
				.advisors(advisorParams(conversationId, category))
				.stream()
				.content();
	}

	/** 대화 이력 조회. */
	public List<Message> getHistory(String conversationId) {
		return chatMemory.get(conversationId);
	}

	/** 대화 이력 초기화. */
	public void clearHistory(String conversationId) {
		chatMemory.clear(conversationId);
	}

	/** advisor 공통 파라미터 설정. */
	private Consumer<AdvisorSpec> advisorParams(String conversationId, String category) {
		return a -> {
			a.param(ChatMemory.CONVERSATION_ID, conversationId);
			if (category != null && !category.isBlank()) {
				a.param(FILTER_EXPRESSION, "category == '" + category + "'");
			}
		};
	}
}
