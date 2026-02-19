package com.example.rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	private final ChatClient chatClient;
	private final VectorStore vectorStore;

	public ChatService(ChatClient chatClient, VectorStore vectorStore) {
		this.chatClient = chatClient;
		this.vectorStore = vectorStore;
	}

	public String ask(String question) {
		return chatClient.prompt()
				.user(question)
				.advisors(QuestionAnswerAdvisor.builder(vectorStore)
						.searchRequest(SearchRequest.builder().topK(5).build())
						.build())
				.call()
				.content();
	}
}
