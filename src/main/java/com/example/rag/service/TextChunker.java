package com.example.rag.service;

import com.example.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting text into chunks with overlap.
 * Uses a recursive splitting strategy to preserve semantic boundaries.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TextChunker {

    private final RagProperties ragProperties;

    /**
     * Split text into chunks with specified size and overlap.
     *
     * @param text the text to split
     * @return list of text chunks
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = ragProperties.getChunking().getChunkSize();
        int overlap = ragProperties.getChunking().getChunkOverlap();

        log.debug("Chunking text of length {} with chunkSize={}, overlap={}",
            text.length(), chunkSize, overlap);

        List<String> chunks = new ArrayList<>();

        // Convert chunk size from tokens to approximate characters
        // Rough estimate: 1 token ≈ 4 characters
        int chunkSizeChars = chunkSize * 4;
        int overlapChars = overlap * 4;

        // Split by paragraphs first
        String[] paragraphs = text.split("\n\n+");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            // If adding this paragraph would exceed chunk size
            if (currentChunk.length() + paragraph.length() > chunkSizeChars && currentChunk.length() > 0) {
                // Save current chunk
                chunks.add(currentChunk.toString().trim());

                // Start new chunk with overlap from previous chunk
                String overlapText = getLastNCharacters(currentChunk.toString(), overlapChars);
                currentChunk = new StringBuilder(overlapText);
                if (!overlapText.isEmpty() && !paragraph.isBlank()) {
                    currentChunk.append("\n\n");
                }
            }

            // If paragraph itself is too large, split by sentences
            if (paragraph.length() > chunkSizeChars) {
                List<String> sentenceChunks = splitBySentences(paragraph, chunkSizeChars, overlapChars);
                for (String sentenceChunk : sentenceChunks) {
                    if (currentChunk.length() + sentenceChunk.length() > chunkSizeChars && currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        String overlapText = getLastNCharacters(currentChunk.toString(), overlapChars);
                        currentChunk = new StringBuilder(overlapText);
                        if (!overlapText.isEmpty() && !sentenceChunk.isBlank()) {
                            currentChunk.append(" ");
                        }
                    }
                    currentChunk.append(sentenceChunk).append(" ");
                }
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }

        // Add the last chunk if it has content
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        log.debug("Created {} chunks from text", chunks.size());
        return chunks;
    }

    /**
     * Split text by sentences when paragraphs are too large.
     */
    private List<String> splitBySentences(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // Split by sentence boundaries (., !, ?)
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                String overlapText = getLastNCharacters(currentChunk.toString(), overlap);
                currentChunk = new StringBuilder(overlapText);
                if (!overlapText.isEmpty() && !sentence.isBlank()) {
                    currentChunk.append(" ");
                }
            }

            // If single sentence is larger than chunk size, split by characters
            if (sentence.length() > chunkSize) {
                List<String> charChunks = splitByCharacters(sentence, chunkSize, overlap);
                chunks.addAll(charChunks);
            } else {
                currentChunk.append(sentence).append(" ");
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * Split text by characters as a last resort.
     */
    private List<String> splitByCharacters(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += (chunkSize - overlap);
        }

        return chunks;
    }

    /**
     * Get the last N characters from a string.
     */
    private String getLastNCharacters(String text, int n) {
        if (text.length() <= n) {
            return text;
        }
        return text.substring(text.length() - n);
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters).
     * For production, use a proper tokenizer like tiktoken.
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.length() / 4;
    }
}
