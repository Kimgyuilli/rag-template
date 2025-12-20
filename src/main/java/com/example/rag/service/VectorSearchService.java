package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.model.dto.SourceReference;
import com.example.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for vector similarity search using pgvector.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

    private final DocumentChunkRepository repository;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    /**
     * Search for similar document chunks based on a question.
     *
     * @param question the user's question
     * @param topK number of results to return
     * @param threshold similarity threshold (0-1)
     * @return list of similar chunks with source information
     */
    public List<SourceReference> searchSimilar(String question, Integer topK, Double threshold) {
        try {
            long startTime = System.currentTimeMillis();

            // Use configuration defaults if not specified
            int k = (topK != null) ? topK : ragProperties.getRetrieval().getTopK();
            double t = (threshold != null) ? threshold : ragProperties.getRetrieval().getSimilarityThreshold();

            log.info("Searching for similar chunks: question='{}', topK={}, threshold={}",
                question, k, t);

            // 1. Generate embedding for the question
            float[] questionEmbedding = embeddingService.embed(question);

            // 2. Convert float[] to PostgreSQL vector format string
            String embeddingStr = floatArrayToVectorString(questionEmbedding);

            // 3. Search using pgvector
            List<Object[]> results = repository.findSimilarChunks(embeddingStr, t, k);

            log.info("Found {} similar chunks in {}ms",
                results.size(), System.currentTimeMillis() - startTime);

            // 4. Convert results to SourceReference objects
            List<SourceReference> sources = new ArrayList<>();
            for (Object[] row : results) {
                SourceReference source = SourceReference.builder()
                    .documentId((String) row[1])
                    .filename((String) row[2])
                    .chunkIndex((Integer) row[3])
                    .content((String) row[4])
                    .sourceType((String) row[6])
                    .similarity(((Number) row[8]).doubleValue())
                    .build();
                sources.add(source);
            }

            return sources;

        } catch (Exception e) {
            log.error("Failed to search similar chunks", e);
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert float array to PostgreSQL vector format string.
     * Example: [0.1, 0.2, 0.3] -> "[0.1,0.2,0.3]"
     */
    private String floatArrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
