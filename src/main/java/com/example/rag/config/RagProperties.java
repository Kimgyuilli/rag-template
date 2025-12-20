package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the RAG application.
 * Maps to the 'rag' prefix in application.yaml.
 */
@Configuration
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    private OpenAI openai = new OpenAI();
    private Chunking chunking = new Chunking();
    private Retrieval retrieval = new Retrieval();
    private Documents documents = new Documents();

    @Data
    public static class OpenAI {
        /**
         * OpenAI API key for embeddings and chat completion.
         */
        private String apiKey;

        /**
         * Embedding model to use (e.g., text-embedding-ada-002).
         */
        private String embeddingModel = "text-embedding-ada-002";

        /**
         * Chat model to use (e.g., gpt-3.5-turbo).
         */
        private String chatModel = "gpt-3.5-turbo";

        /**
         * Request timeout in seconds.
         */
        private String timeout = "60s";

        /**
         * Maximum number of retries for API calls.
         */
        private int maxRetries = 3;
    }

    @Data
    public static class Chunking {
        /**
         * Target chunk size in tokens.
         */
        private int chunkSize = 1000;

        /**
         * Number of tokens to overlap between chunks.
         */
        private int chunkOverlap = 200;

        /**
         * Minimum chunk size in tokens.
         */
        private int minChunkSize = 100;
    }

    @Data
    public static class Retrieval {
        /**
         * Number of chunks to retrieve for each query.
         */
        private int topK = 3;

        /**
         * Minimum similarity threshold for retrieval (0-1).
         */
        private double similarityThreshold = 0.7;
    }

    @Data
    public static class Documents {
        /**
         * Comma-separated list of allowed file extensions.
         */
        private String allowedExtensions = "txt,md";

        /**
         * Path to store uploaded documents (optional).
         */
        private String storagePath = "./data/documents";
    }
}
