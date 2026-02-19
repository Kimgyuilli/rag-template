package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

	private static final String SYSTEM_PROMPT = """
			당신은 고객센터 상담 챗봇입니다.
			아래 규칙을 따르세요:
			1. 항상 한국어로 답변하세요.
			2. 제공된 컨텍스트 정보를 기반으로 정확하게 답변하세요.
			3. 컨텍스트에 관련 정보가 없으면 "해당 내용에 대한 정보를 찾을 수 없습니다. 고객센터(1234-5678)로 문의해 주세요."라고 안내하세요.
			4. 답변은 친절하고 간결하게 작성하세요.
			""";

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}
}
