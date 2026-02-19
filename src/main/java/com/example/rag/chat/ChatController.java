package com.example.rag.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	record ChatRequest(@NotBlank String question) {
	}

	record ChatResponse(String answer) {
	}

	@PostMapping
	ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return new ChatResponse(chatService.ask(request.question()));
	}
}
