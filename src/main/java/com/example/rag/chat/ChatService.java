package com.example.rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class ChatService {

	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	private final ChatMemory chatMemory;

	public ChatService(ChatClient chatClient, VectorStore vectorStore, ChatMemory chatMemory) {
		this.chatClient = chatClient;
		this.vectorStore = vectorStore;
		this.chatMemory = chatMemory;
	}

	public String ask(String question, String conversationId) {
		return chatClient.prompt()
				.user(question)
				.advisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
						QuestionAnswerAdvisor.builder(vectorStore)
								.searchRequest(SearchRequest.builder().topK(5).build())
								.build())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
	}

	public Flux<String> askStream(String question, String conversationId) {
		return chatClient.prompt()
				.user(question)
				.advisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
						QuestionAnswerAdvisor.builder(vectorStore)
								.searchRequest(SearchRequest.builder().topK(5).build())
								.build())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.stream()
				.content();
	}
}
