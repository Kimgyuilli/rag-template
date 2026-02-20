package com.example.rag.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

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

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
		return chatService.askStream(request.question());
	}
}
