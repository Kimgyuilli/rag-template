package com.example.rag.document;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 문서 CRUD API 컨트롤러.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	/** 문서를 청크 분할 후 벡터 저장소에 등록한다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	IngestResponse ingest(@Valid @RequestBody IngestRequest request) {
		UUID documentId = documentService.ingest(request.title(), request.content(), request.category());
		return new IngestResponse("문서가 등록되었습니다.", documentId);
	}

	/** 문서 목록 조회. */
	@GetMapping
	List<DocumentSummary> list() {
		return documentService.list();
	}

	/** 문서 상세 조회. */
	@GetMapping("/{documentId}")
	ResponseEntity<DocumentDetail> getById(@PathVariable UUID documentId) {
		DocumentDetail detail = documentService.getById(documentId);
		if (detail == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(detail);
	}

	/** 문서 수정 (삭제 후 재등록). */
	@PutMapping("/{documentId}")
	ResponseEntity<IngestResponse> update(@PathVariable UUID documentId,
			@Valid @RequestBody IngestRequest request) {
		boolean updated = documentService.update(documentId, request.title(), request.content(), request.category());
		if (!updated) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(new IngestResponse("문서가 수정되었습니다.", documentId));
	}

	/** 문서 삭제. */
	@DeleteMapping("/{documentId}")
	ResponseEntity<Void> delete(@PathVariable UUID documentId) {
		boolean deleted = documentService.delete(documentId);
		if (!deleted) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
