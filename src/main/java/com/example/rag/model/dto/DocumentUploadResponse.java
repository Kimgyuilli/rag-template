package com.example.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for document upload endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    /**
     * Unique identifier for the uploaded document.
     */
    private String documentId;

    /**
     * Original filename.
     */
    private String filename;

    /**
     * Processing status.
     */
    private String status;

    /**
     * Number of chunks created from the document.
     */
    private Integer chunksCreated;

    /**
     * Total number of tokens processed (estimated).
     */
    private Integer totalTokens;

    /**
     * Success or error message.
     */
    private String message;

    /**
     * Timestamp of the response.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
