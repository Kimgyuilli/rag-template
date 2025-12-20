package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.model.dto.DocumentUploadResponse;
import com.example.rag.model.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for processing and storing documents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentChunkRepository repository;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;
    private final Tika tika = new Tika();

    /**
     * Process uploaded document: extract text, chunk, embed, and store.
     *
     * @param file the uploaded file
     * @return processing result
     */
    @Transactional
    public DocumentUploadResponse processDocument(MultipartFile file) {
        try {
            // 1. Validate file
            validateFile(file);

            String filename = file.getOriginalFilename();
            String documentId = UUID.randomUUID().toString();

            log.info("Processing document: {} (ID: {})", filename, documentId);

            // 2. Extract text using Apache Tika
            String text = extractText(file);
            log.debug("Extracted {} characters from {}", text.length(), filename);

            // 3. Chunk the text
            List<String> chunks = textChunker.chunk(text);
            log.info("Created {} chunks from {}", chunks.size(), filename);

            if (chunks.isEmpty()) {
                return DocumentUploadResponse.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .status("failed")
                    .chunksCreated(0)
                    .message("No content to process")
                    .build();
            }

            // 4. Generate embeddings for all chunks
            log.info("Generating embeddings for {} chunks", chunks.size());
            List<float[]> embeddings = embeddingService.embedBatch(chunks);

            if (embeddings.size() != chunks.size()) {
                throw new RuntimeException("Embedding count mismatch: " +
                    embeddings.size() + " != " + chunks.size());
            }

            // 5. Create and save DocumentChunk entities
            List<DocumentChunk> documentChunks = new ArrayList<>();
            String sourceType = getFileExtension(filename);
            int totalTokens = 0;

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = embeddings.get(i);

                DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(documentId)
                    .filename(filename)
                    .chunkIndex(i)
                    .content(chunkText)
                    .embedding(embedding)
                    .sourceType(sourceType)
                    .build();

                documentChunks.add(chunk);
                totalTokens += textChunker.estimateTokenCount(chunkText);
            }

            // 6. Save to database
            repository.saveAll(documentChunks);
            log.info("Saved {} chunks to database for document {}", documentChunks.size(), documentId);

            return DocumentUploadResponse.builder()
                .documentId(documentId)
                .filename(filename)
                .status("processed")
                .chunksCreated(chunks.size())
                .totalTokens(totalTokens)
                .message("Document processed successfully")
                .build();

        } catch (Exception e) {
            log.error("Failed to process document: {}", file.getOriginalFilename(), e);
            return DocumentUploadResponse.builder()
                .filename(file.getOriginalFilename())
                .status("failed")
                .chunksCreated(0)
                .message("Processing failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Validate uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Check file extension
        String extension = getFileExtension(filename);
        String allowedExtensions = ragProperties.getDocuments().getAllowedExtensions();
        List<String> allowed = List.of(allowedExtensions.split(","));

        if (!allowed.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                "File type not allowed. Allowed extensions: " + allowedExtensions);
        }

        log.debug("File validation passed: {}", filename);
    }

    /**
     * Extract text from file using Apache Tika.
     */
    private String extractText(MultipartFile file) {
        try {
            String text = tika.parseToString(file.getInputStream());

            if (text == null || text.isBlank()) {
                throw new RuntimeException("No text content extracted from file");
            }

            return text;

        } catch (Exception e) {
            log.error("Failed to extract text from file", e);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Delete a document and all its chunks.
     *
     * @param documentId the document ID
     */
    @Transactional
    public void deleteDocument(String documentId) {
        log.info("Deleting document: {}", documentId);
        repository.deleteByDocumentId(documentId);
    }

    /**
     * Get all chunks for a specific document.
     *
     * @param documentId the document ID
     * @return list of chunks
     */
    public List<DocumentChunk> getDocumentChunks(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }
}
