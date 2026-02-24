package com.example.rag.document.dto;

import java.util.UUID;

public record IngestResponse(String message, UUID documentId) {
}
