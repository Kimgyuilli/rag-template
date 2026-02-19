package com.example.rag.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	record IngestRequest(
			@NotBlank String title,
			@NotBlank String content,
			String category) {
	}

	record IngestResponse(String message) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	IngestResponse ingest(@Valid @RequestBody IngestRequest request) {
		documentService.ingest(request.title(), request.content(), request.category());
		return new IngestResponse("문서가 등록되었습니다.");
	}
}
