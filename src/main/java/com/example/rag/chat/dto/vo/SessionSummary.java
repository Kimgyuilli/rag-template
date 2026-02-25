package com.example.rag.chat.dto.vo;

import java.time.LocalDateTime;

public record SessionSummary(String conversationId, String title, LocalDateTime createdAt) {
}
