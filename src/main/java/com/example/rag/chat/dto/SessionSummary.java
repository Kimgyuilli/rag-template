package com.example.rag.chat.dto;

import java.time.LocalDateTime;

public record SessionSummary(String conversationId, String title, LocalDateTime createdAt) {
}
