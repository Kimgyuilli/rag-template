package com.example.rag.controller;

import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for RAG-based chat.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * Process a chat question using RAG.
     *
     * POST /api/v1/chat
     *
     * @param request chat request with question
     * @return chat response with answer and sources
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getQuestion());

        try {
            ChatResponse response = chatService.chat(request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid chat request: {}", e.getMessage());
            ChatResponse errorResponse = ChatResponse.builder()
                .question(request.getQuestion())
                .answer("Invalid request: " + e.getMessage())
                .build();
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during chat processing", e);
            ChatResponse errorResponse = ChatResponse.builder()
                .question(request.getQuestion())
                .answer("Internal server error: " + e.getMessage())
                .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
