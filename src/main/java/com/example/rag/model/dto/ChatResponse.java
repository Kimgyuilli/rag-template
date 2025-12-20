package com.example.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * The user's original question.
     */
    private String question;

    /**
     * The generated answer.
     */
    private String answer;

    /**
     * Source references used to generate the answer.
     */
    private List<SourceReference> sources;

    /**
     * Processing time in milliseconds.
     */
    private Long processingTimeMs;

    /**
     * Model used for generation.
     */
    private String model;

    /**
     * Timestamp of the response.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
