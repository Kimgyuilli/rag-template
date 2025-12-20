package com.example.rag.integration;

import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.model.dto.DocumentUploadResponse;
import com.example.rag.repository.DocumentChunkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for RAG chat flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentChunkRepository repository;

    private String testDocumentId;

    @BeforeEach
    void setUp() {
        // Upload a test document before each test
        String testContent = """
            RAG System Guide

            The RAG system supports the following file formats: .txt and .md
            Maximum file size is 10MB.
            The system uses OpenAI's text-embedding-ada-002 model for embeddings.
            Documents are chunked into 1000 token pieces with 200 token overlap.
            """;

        ByteArrayResource fileResource = new ByteArrayResource(testContent.getBytes()) {
            @Override
            public String getFilename() {
                return "rag-guide.txt";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String uploadUrl = "http://localhost:" + port + "/api/v1/documents/upload";
        ResponseEntity<DocumentUploadResponse> uploadResponse = restTemplate.postForEntity(
            uploadUrl,
            requestEntity,
            DocumentUploadResponse.class
        );

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        testDocumentId = uploadResponse.getBody().getDocumentId();
    }

    @AfterEach
    void tearDown() {
        // Clean up test document
        if (testDocumentId != null) {
            repository.deleteByDocumentId(testDocumentId);
        }
    }

    @Test
    void testChatWithContext() {
        // Given: A question about the uploaded document
        ChatRequest request = ChatRequest.builder()
            .question("What file formats does the RAG system support?")
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> requestEntity = new HttpEntity<>(request, headers);

        // When: Send chat request
        String chatUrl = "http://localhost:" + port + "/api/v1/chat";
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
            chatUrl,
            requestEntity,
            ChatResponse.class
        );

        // Then: Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getQuestion()).isEqualTo(request.getQuestion());
        assertThat(response.getBody().getAnswer()).isNotBlank();
        assertThat(response.getBody().getSources()).isNotEmpty();
        assertThat(response.getBody().getProcessingTimeMs()).isGreaterThan(0L);
        assertThat(response.getBody().getModel()).isNotBlank();

        // Verify sources contain relevant information
        assertThat(response.getBody().getSources().get(0).getFilename()).isEqualTo("rag-guide.txt");
        assertThat(response.getBody().getSources().get(0).getSimilarity()).isGreaterThan(0.5);
    }

    @Test
    void testChatWithCustomParameters() {
        // Given: A chat request with custom topK and threshold
        ChatRequest request = ChatRequest.builder()
            .question("What is the maximum file size?")
            .topK(5)
            .similarityThreshold(0.6)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> requestEntity = new HttpEntity<>(request, headers);

        // When: Send chat request
        String chatUrl = "http://localhost:" + port + "/api/v1/chat";
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
            chatUrl,
            requestEntity,
            ChatResponse.class
        );

        // Then: Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAnswer()).isNotBlank();
        assertThat(response.getBody().getSources()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void testChatWithoutContext() {
        // Given: A question unrelated to uploaded documents
        ChatRequest request = ChatRequest.builder()
            .question("What is quantum computing?")
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> requestEntity = new HttpEntity<>(request, headers);

        // When: Send chat request
        String chatUrl = "http://localhost:" + port + "/api/v1/chat";
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
            chatUrl,
            requestEntity,
            ChatResponse.class
        );

        // Then: Verify response indicates no relevant context
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Sources might be empty or have low similarity scores
        if (!response.getBody().getSources().isEmpty()) {
            assertThat(response.getBody().getSources().get(0).getSimilarity()).isLessThan(0.7);
        }
    }

    @Test
    void testChatWithEmptyQuestion() {
        // Given: An empty question
        ChatRequest request = ChatRequest.builder()
            .question("")
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> requestEntity = new HttpEntity<>(request, headers);

        // When: Send chat request
        String chatUrl = "http://localhost:" + port + "/api/v1/chat";
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
            chatUrl,
            requestEntity,
            ChatResponse.class
        );

        // Then: Verify error response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
