package com.example.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param question       사용자 질문 (필수)
 * @param conversationId 대화 세션 ID (선택 — 없으면 서버에서 UUID 생성)
 */
public record ChatRequest(@NotBlank String question, String conversationId, String category) {
}
