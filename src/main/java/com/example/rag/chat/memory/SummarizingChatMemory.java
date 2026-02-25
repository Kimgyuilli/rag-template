package com.example.rag.chat.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 임계값 초과 시 오래된 메시지를 LLM으로 요약하는 ChatMemory 구현.
 *
 * 메시지 수가 maxMessages를 초과하면:
 * 1. 최근 keepRecent개를 원본 유지
 * 2. 나머지를 LLM으로 요약
 * 3. [요약 SystemMessage] + [최근 메시지]로 교체하여 저장
 */
public class SummarizingChatMemory implements ChatMemory {

	private static final String SUMMARY_PROMPT = """
			다음은 사용자와 AI 어시스턴트 사이의 이전 대화 내용입니다.
			핵심 정보(사용자 이름, 요청 사항, 중요한 결정 등)를 빠짐없이 포함하여
			간결하게 한국어로 요약해 주세요.

			대화 내용:
			%s
			""";

	private final ChatMemoryRepository chatMemoryRepository;
	private final ChatModel chatModel;
	private final int maxMessages;
	private final int keepRecent;

	private SummarizingChatMemory(Builder builder) {
		this.chatMemoryRepository = builder.chatMemoryRepository;
		this.chatModel = builder.chatModel;
		this.maxMessages = builder.maxMessages;
		this.keepRecent = builder.keepRecent;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		List<Message> existing = chatMemoryRepository.findByConversationId(conversationId);
		List<Message> all = new ArrayList<>(existing);
		all.addAll(messages);
		chatMemoryRepository.saveAll(conversationId, all);
	}

	@Override
	public List<Message> get(String conversationId) {
		List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);

		if (messages.size() <= maxMessages) {
			return messages;
		}

		// 최근 keepRecent개 분리
		List<Message> recent = messages.subList(messages.size() - keepRecent, messages.size());
		List<Message> older = messages.subList(0, messages.size() - keepRecent);

		// 오래된 메시지 요약
		String summary = summarize(older);
		SystemMessage summaryMessage = new SystemMessage("[이전 대화 요약] " + summary);

		// 요약 + 최근 메시지로 교체 저장
		List<Message> condensed = new ArrayList<>();
		condensed.add(summaryMessage);
		condensed.addAll(recent);
		chatMemoryRepository.saveAll(conversationId, condensed);

		return condensed;
	}

	@Override
	public void clear(String conversationId) {
		chatMemoryRepository.deleteByConversationId(conversationId);
	}

	private String summarize(List<Message> messages) {
		StringBuilder sb = new StringBuilder();
		for (Message msg : messages) {
			sb.append(msg.getMessageType().name()).append(": ").append(msg.getText()).append("\n");
		}
		String prompt = SUMMARY_PROMPT.formatted(sb.toString());
		return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private ChatMemoryRepository chatMemoryRepository;
		private ChatModel chatModel;
		private int maxMessages = 20;
		private int keepRecent = 10;

		public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
			this.chatMemoryRepository = chatMemoryRepository;
			return this;
		}

		public Builder chatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public Builder maxMessages(int maxMessages) {
			this.maxMessages = maxMessages;
			return this;
		}

		public Builder keepRecent(int keepRecent) {
			this.keepRecent = keepRecent;
			return this;
		}

		public SummarizingChatMemory build() {
			return new SummarizingChatMemory(this);
		}
	}
}
