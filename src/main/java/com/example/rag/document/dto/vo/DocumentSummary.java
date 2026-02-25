package com.example.rag.document.dto.vo;

import java.util.UUID;

public record DocumentSummary(UUID documentId, String title, String category, int chunkCount) {
}
