package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.model.dto.SourceReference;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for RAG-based chat completion.
 * Orchestrates the entire RAG pipeline: search -> prompt building -> generation.
 */
@Service
@Slf4j
public class ChatService {

    private final VectorSearchService vectorSearchService;
    private final PromptBuilder promptBuilder;
    private final OpenAiService openAiService;
    private final RagProperties ragProperties;

    public ChatService(
        VectorSearchService vectorSearchService,
        PromptBuilder promptBuilder,
        RagProperties ragProperties
    ) {
        this.vectorSearchService = vectorSearchService;
        this.promptBuilder = promptBuilder;
        this.ragProperties = ragProperties;

        // Parse timeout
        String timeoutStr = ragProperties.getOpenai().getTimeout();
        Duration timeout = parseDuration(timeoutStr);

        this.openAiService = new OpenAiService(
            ragProperties.getOpenai().getApiKey(),
            timeout
        );

        log.info("Chat service initialized with model: {}",
            ragProperties.getOpenai().getChatModel());
    }

    /**
     * Process a chat request using RAG pipeline.
     *
     * @param request chat request
     * @return chat response with answer and sources
     */
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String question = request.getQuestion();
            log.info("Processing chat request: {}", question);

            // 1. Vector similarity search
            List<SourceReference> sources = vectorSearchService.searchSimilar(
                question,
                request.getTopK(),
                request.getSimilarityThreshold()
            );

            log.info("Retrieved {} relevant sources", sources.size());

            // 2. Build prompt with context
            List<PromptBuilder.ChatMessage> messages = promptBuilder.buildChatMessages(question, sources);

            // 3. Call OpenAI Chat Completion API
            String answer = generateAnswer(messages);

            // 4. Build response
            long processingTime = System.currentTimeMillis() - startTime;

            return ChatResponse.builder()
                .question(question)
                .answer(answer)
                .sources(sources)
                .processingTimeMs(processingTime)
                .model(ragProperties.getOpenai().getChatModel())
                .build();

        } catch (Exception e) {
            log.error("Failed to process chat request", e);
            throw new RuntimeException("Chat processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate answer using OpenAI Chat Completion API.
     */
    private String generateAnswer(List<PromptBuilder.ChatMessage> messages) {
        try {
            // Convert to OpenAI ChatMessage format
            List<ChatMessage> openaiMessages = messages.stream()
                .map(msg -> new ChatMessage(msg.role(), msg.content()))
                .collect(Collectors.toList());

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .model(ragProperties.getOpenai().getChatModel())
                .messages(openaiMessages)
                .temperature(0.7)
                .maxTokens(500)
                .build();

            log.debug("Calling OpenAI Chat Completion API");

            ChatCompletionResult result = openAiService.createChatCompletion(completionRequest);

            if (result.getChoices() == null || result.getChoices().isEmpty()) {
                throw new RuntimeException("No response from OpenAI API");
            }

            String answer = result.getChoices().get(0).getMessage().getContent();
            log.debug("Generated answer of length: {}", answer.length());

            return answer;

        } catch (Exception e) {
            log.error("Failed to generate answer", e);
            throw new RuntimeException("Answer generation failed: " + e.getMessage(), e);
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
                return Duration.ofSeconds(Long.parseLong(durationStr));
            }
        } catch (Exception e) {
            log.warn("Failed to parse duration '{}', using default 60s", durationStr);
            return Duration.ofSeconds(60);
        }
    }
}
