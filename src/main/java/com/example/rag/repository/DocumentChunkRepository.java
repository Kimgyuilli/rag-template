package com.example.rag.repository;

import com.example.rag.model.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DocumentChunk entity with pgvector support.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Find all chunks belonging to a specific document.
     *
     * @param documentId the document ID
     * @return list of chunks ordered by chunk index
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    /**
     * Delete all chunks belonging to a specific document.
     *
     * @param documentId the document ID
     */
    void deleteByDocumentId(String documentId);

    /**
     * Count chunks for a specific document.
     *
     * @param documentId the document ID
     * @return number of chunks
     */
    long countByDocumentId(String documentId);

    /**
     * Find similar chunks using cosine distance (pgvector).
     * Returns top K most similar chunks above the similarity threshold.
     *
     * The query uses the <=> operator for cosine distance.
     * Similarity is calculated as: 1 - cosine_distance
     *
     * @param embedding the query embedding vector as String (e.g., "[0.1, 0.2, ...]")
     * @param threshold minimum similarity threshold (0-1)
     * @param limit maximum number of results to return
     * @return list of similar chunks with their similarity scores
     */
    @Query(value = """
        SELECT
            dc.id,
            dc.document_id,
            dc.filename,
            dc.chunk_index,
            dc.content,
            dc.embedding,
            dc.source_type,
            dc.created_at,
            (1 - (dc.embedding <=> CAST(:embedding AS vector))) AS similarity
        FROM document_chunks dc
        WHERE (1 - (dc.embedding <=> CAST(:embedding AS vector))) > :threshold
        ORDER BY dc.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
        @Param("embedding") String embedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit
    );

    /**
     * Check if a document with the given ID exists.
     *
     * @param documentId the document ID
     * @return true if exists, false otherwise
     */
    boolean existsByDocumentId(String documentId);
}
