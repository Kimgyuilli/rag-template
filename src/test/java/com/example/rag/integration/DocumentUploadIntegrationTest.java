package com.example.rag.integration;

import com.example.rag.model.dto.DocumentUploadResponse;
import com.example.rag.model.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for document upload and processing flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DocumentUploadIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentChunkRepository repository;

    @Test
    void testDocumentUploadFlow() {
        // Given: A test document
        String testContent = """
            RAG System Test Document

            This is a test document for the RAG system.
            It contains multiple paragraphs to test the chunking functionality.

            The system should:
            1. Extract text from the file
            2. Split it into chunks
            3. Generate embeddings
            4. Store in the database
            """;

        ByteArrayResource fileResource = new ByteArrayResource(testContent.getBytes()) {
            @Override
            public String getFilename() {
                return "test-upload.txt";
            }
        };

        // When: Upload the document
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String url = "http://localhost:" + port + "/api/v1/documents/upload";
        ResponseEntity<DocumentUploadResponse> response = restTemplate.postForEntity(
            url,
            requestEntity,
            DocumentUploadResponse.class
        );

        // Then: Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("processed");
        assertThat(response.getBody().getDocumentId()).isNotNull();
        assertThat(response.getBody().getChunksCreated()).isGreaterThan(0);

        // And: Verify chunks are stored in database
        String documentId = response.getBody().getDocumentId();
        List<DocumentChunk> chunks = repository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isEqualTo(response.getBody().getChunksCreated());

        // Verify chunk contents
        for (DocumentChunk chunk : chunks) {
            assertThat(chunk.getDocumentId()).isEqualTo(documentId);
            assertThat(chunk.getFilename()).isEqualTo("test-upload.txt");
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getEmbedding()).isNotNull();
            assertThat(chunk.getEmbedding().length).isEqualTo(1536); // text-embedding-ada-002 dimension
        }

        // Cleanup
        repository.deleteByDocumentId(documentId);
    }

    @Test
    void testInvalidFileUpload() {
        // Given: An invalid file (wrong extension)
        ByteArrayResource fileResource = new ByteArrayResource("test".getBytes()) {
            @Override
            public String getFilename() {
                return "test.exe";
            }
        };

        // When: Upload the document
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String url = "http://localhost:" + port + "/api/v1/documents/upload";
        ResponseEntity<DocumentUploadResponse> response = restTemplate.postForEntity(
            url,
            requestEntity,
            DocumentUploadResponse.class
        );

        // Then: Verify error response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("failed");
    }
}
