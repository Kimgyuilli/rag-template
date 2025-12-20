package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating embeddings using OpenAI API.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final OpenAiService openAiService;
    private final RagProperties ragProperties;

    public EmbeddingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;

        // Parse timeout string (e.g., "60s" -> Duration.ofSeconds(60))
        String timeoutStr = ragProperties.getOpenai().getTimeout();
        Duration timeout = parseDuration(timeoutStr);

        this.openAiService = new OpenAiService(
            ragProperties.getOpenai().getApiKey(),
            timeout
        );

        log.info("Embedding service initialized with model: {}",
            ragProperties.getOpenai().getEmbeddingModel());
    }

    /**
     * Generate embedding for a single text.
     *
     * @param text the text to embed
     * @return embedding vector as float array
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }

        try {
            log.debug("Generating embedding for text of length {}", text.length());

            EmbeddingRequest request = EmbeddingRequest.builder()
                .model(ragProperties.getOpenai().getEmbeddingModel())
                .input(List.of(text))
                .build();

            List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

            if (embeddings.isEmpty()) {
                throw new RuntimeException("No embedding returned from OpenAI API");
            }

            // Convert List<Double> to float[]
            List<Double> embeddingList = embeddings.get(0).getEmbedding();
            float[] result = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                result[i] = embeddingList.get(i).floatValue();
            }

            log.debug("Generated embedding of dimension {}", result.length);
            return result;

        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for multiple texts in batch.
     * OpenAI API supports up to 100 texts per request.
     *
     * @param texts list of texts to embed
     * @return list of embedding vectors
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        // Filter out null or blank texts
        List<String> validTexts = texts.stream()
            .filter(text -> text != null && !text.isBlank())
            .collect(Collectors.toList());

        if (validTexts.isEmpty()) {
            return List.of();
        }

        try {
            log.debug("Generating {} embeddings in batch", validTexts.size());

            // OpenAI API limit: 100 inputs per request
            int batchSize = 100;
            List<float[]> allEmbeddings = new ArrayList<>();

            for (int i = 0; i < validTexts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, validTexts.size());
                List<String> batch = validTexts.subList(i, end);

                EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(ragProperties.getOpenai().getEmbeddingModel())
                    .input(batch)
                    .build();

                List<Embedding> embeddings = openAiService.createEmbeddings(request).getData();

                for (Embedding embedding : embeddings) {
                    List<Double> embeddingList = embedding.getEmbedding();
                    float[] result = new float[embeddingList.size()];
                    for (int j = 0; j < embeddingList.size(); j++) {
                        result[j] = embeddingList.get(j).floatValue();
                    }
                    allEmbeddings.add(result);
                }

                log.debug("Processed batch {}-{} of {}", i, end, validTexts.size());
            }

            log.info("Generated {} embeddings successfully", allEmbeddings.size());
            return allEmbeddings;

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            throw new RuntimeException("Failed to generate batch embeddings: " + e.getMessage(), e);
        }
    }

    /**
     * Parse duration string (e.g., "60s", "1m", "1h") to Duration.
     */
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return Duration.ofSeconds(60);
        }

        try {
            if (durationStr.endsWith("s")) {
                long seconds = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
                return Duration.ofSeconds(seconds);
            } else if (durationStr.endsWith("m")) {
                long minutes = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
                return Duration.ofMinutes(minutes);
            } else if (durationStr.endsWith("h")) {
                long hours = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
                return Duration.ofHours(hours);
            } else {
                // Assume seconds if no unit
                return Duration.ofSeconds(Long.parseLong(durationStr));
            }
        } catch (Exception e) {
            log.warn("Failed to parse duration '{}', using default 60s", durationStr);
            return Duration.ofSeconds(60);
        }
    }
}
