package com.example.rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class ChatService {

	private final ChatClient chatClient;

	public ChatService(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public String ask(String question, String conversationId) {
		return chatClient.prompt()
				.user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
	}

	public Flux<String> askStream(String question, String conversationId) {
		return chatClient.prompt()
				.user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.stream()
				.content();
	}
}
