package com.example.rag.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentSummary(UUID documentId, String title, String category, int chunkCount,
							  LocalDateTime createdAt) {
}
