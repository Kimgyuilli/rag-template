package com.example.rag.chat;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 채팅 API 컨트롤러.
 * 동기({@code POST /api/chat})와 스트리밍({@code POST /api/chat/stream}) 엔드포인트를 제공한다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	/**
	 * @param question       사용자 질문 (필수)
	 * @param conversationId 대화 세션 ID (선택 — 없으면 서버에서 UUID 생성)
	 */
	record ChatRequest(@NotBlank String question, String conversationId) {
	}

	record ChatResponse(String answer, String conversationId) {
	}

	/** 동기 방식 채팅 API. */
	@PostMapping
	ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		String conversationId = resolveConversationId(request.conversationId());
		return new ChatResponse(chatService.ask(request.question(), conversationId), conversationId);
	}

	/**
	 * 스트리밍 방식 채팅 API.
	 * 응답 끝에 {@code conversationId} SSE 이벤트를 전송하여 클라이언트가 세션을 추적할 수 있게 한다.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequest request) {
		String conversationId = resolveConversationId(request.conversationId());
		return chatService.askStream(request.question(), conversationId)
				.map(token -> ServerSentEvent.builder(token).build())
				.concatWith(Flux.just(ServerSentEvent.<String>builder()
						.event("conversationId")
						.data(conversationId)
						.build()));
	}

	private String resolveConversationId(String conversationId) {
		return (conversationId != null && !conversationId.isBlank())
				? conversationId
				: UUID.randomUUID().toString();
	}
}
