package com.example.rag.document.dto.response;

import java.util.UUID;

public record IngestResponse(String message, UUID documentId) {
}
