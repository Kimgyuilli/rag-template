package com.example.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * User's question.
     */
    @NotBlank(message = "Question cannot be blank")
    private String question;

    /**
     * Optional document ID to search within (null = search all documents).
     */
    private String documentId;

    /**
     * Optional: number of similar chunks to retrieve.
     * If not specified, uses default from configuration.
     */
    private Integer topK;

    /**
     * Optional: similarity threshold (0-1).
     * If not specified, uses default from configuration.
     */
    private Double similarityThreshold;
}
