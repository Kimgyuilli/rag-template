package com.example.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a source reference for RAG responses.
 * Contains information about the document chunk used to generate the answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {

    /**
     * Document ID.
     */
    private String documentId;

    /**
     * Original filename.
     */
    private String filename;

    /**
     * Chunk index in the document.
     */
    private Integer chunkIndex;

    /**
     * The actual content of the chunk.
     */
    private String content;

    /**
     * Similarity score (0-1, higher is more similar).
     */
    private Double similarity;

    /**
     * Source type (file extension).
     */
    private String sourceType;
}
