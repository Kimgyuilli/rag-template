package com.example.rag.document.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(
		@NotBlank String title,
		@NotBlank String content,
		String category) {
}
