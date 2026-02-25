package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.rag.chat.advisor.QueryRewriteAdvisor;
import com.example.rag.chat.advisor.RetrievalRerankAdvisor;

/**
 * AI 관련 빈 설정.
 * ChatClient, ChatMemory 등을 구성하고 시스템 프롬프트와 Advisor 체인을 정의한다.
 *
 * Advisor 실행 순서:
 * MessageChatMemoryAdvisor(order=0) → QueryRewriteAdvisor(order=10) → RetrievalRerankAdvisor(order=20)
 */
@Configuration
public class AiConfig {

	private static final String SYSTEM_PROMPT = """
			당신은 고객센터 상담 챗봇입니다.
			아래 규칙을 따르세요:
			1. 항상 한국어로 답변하세요.
			2. 답변 시 다음 두 가지를 모두 활용하세요:
			   - 이전 대화 내용 (사용자가 언급한 이름, 요청 사항 등)
			   - 제공된 문서 컨텍스트 (상품, 정책 등 참고 자료)
			3. 이전 대화 내용만으로 답변할 수 있으면 문서 컨텍스트 없이도 답변하세요.
			4. 이전 대화에도 문서 컨텍스트에도 관련 정보가 없을 때만 "해당 내용에 대한 정보를 찾을 수 없습니다. 고객센터(1234-5678)로 문의해 주세요."라고 안내하세요.
			5. 답변은 친절하고 간결하게 작성하세요.
			""";

	/**
	 * 대화 이력 저장소.
	 * 최근 20개 메시지를 슬라이딩 윈도우로 유지하며, JDBC를 통해 PostgreSQL에 영속화된다.
	 */
	@Bean
	ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(chatMemoryRepository)
				.maxMessages(20)
				.build();
	}

	/**
	 * ChatClient 구성.
	 * Advisor 체인: 대화 이력 → 쿼리 리라이팅 → 벡터 검색 + 재순위화
	 */
	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
			ChatModel chatModel, VectorStore vectorStore) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(chatMemory).build(),
						new QueryRewriteAdvisor(chatModel, 10),
						new RetrievalRerankAdvisor(vectorStore, chatModel, 20))
				.build();
	}
}
