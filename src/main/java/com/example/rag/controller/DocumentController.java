package com.example.rag.controller;

import com.example.rag.model.dto.DocumentUploadResponse;
import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for document management.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload and process a document.
     *
     * POST /api/v1/documents/upload
     *
     * @param file the file to upload
     * @return processing result
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
        @RequestParam("file") MultipartFile file
    ) {
        log.info("Received document upload request: {}", file.getOriginalFilename());

        try {
            DocumentUploadResponse response = documentService.processDocument(file);

            if ("processed".equals(response.getStatus())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Invalid upload request: {}", e.getMessage());
            DocumentUploadResponse errorResponse = DocumentUploadResponse.builder()
                .filename(file.getOriginalFilename())
                .status("failed")
                .message(e.getMessage())
                .build();
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during document upload", e);
            DocumentUploadResponse errorResponse = DocumentUploadResponse.builder()
                .filename(file.getOriginalFilename())
                .status("failed")
                .message("Internal server error: " + e.getMessage())
                .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Delete a document and all its chunks.
     *
     * DELETE /api/v1/documents/{documentId}
     *
     * @param documentId the document ID
     * @return success message
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<String> deleteDocument(@PathVariable String documentId) {
        log.info("Received delete request for document: {}", documentId);

        try {
            documentService.deleteDocument(documentId);
            return ResponseEntity.ok("Document deleted successfully");

        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to delete document: " + e.getMessage());
        }
    }
}
