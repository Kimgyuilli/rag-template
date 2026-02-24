package com.example.rag.document.dto;

import java.util.UUID;

public record DocumentDetail(UUID documentId, String title, String content, String category, int chunkCount) {
}
