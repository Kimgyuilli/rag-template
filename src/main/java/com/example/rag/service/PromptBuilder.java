package com.example.rag.service;

import com.example.rag.model.dto.SourceReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for building RAG prompts from retrieved context.
 */
@Service
@Slf4j
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
        You are a helpful assistant that answers questions based on the provided context.

        Guidelines:
        1. Answer the question using ONLY the information from the provided context
        2. If the context doesn't contain enough information to answer, say "I don't have enough information to answer this question"
        3. Be concise and accurate
        4. If you quote from the context, indicate which source you're using
        5. If multiple sources provide relevant information, synthesize them coherently
        """;

    /**
     * Build a complete prompt from user question and retrieved context.
     *
     * @param question the user's question
     * @param sources retrieved source references
     * @return formatted prompt for LLM
     */
    public String buildPrompt(String question, List<SourceReference> sources) {
        if (sources == null || sources.isEmpty()) {
            log.warn("No sources provided for question: {}", question);
            return buildPromptWithoutContext(question);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Context from documents:\n\n");

        // Add each source with metadata
        for (int i = 0; i < sources.size(); i++) {
            SourceReference source = sources.get(i);
            prompt.append(String.format("--- Source %d (from %s, chunk %d, similarity: %.2f) ---\n",
                i + 1,
                source.getFilename(),
                source.getChunkIndex(),
                source.getSimilarity()));
            prompt.append(source.getContent());
            prompt.append("\n\n");
        }

        prompt.append("---\n\n");
        prompt.append("Question: ").append(question).append("\n\n");
        prompt.append("Answer based on the context above:");

        String fullPrompt = prompt.toString();
        log.debug("Built prompt with {} sources, total length: {} chars",
            sources.size(), fullPrompt.length());

        return fullPrompt;
    }

    /**
     * Build prompt when no context is available.
     */
    private String buildPromptWithoutContext(String question) {
        return String.format("""
            No relevant context was found in the document database.

            Question: %s

            Please answer: "I don't have any relevant documents to answer this question. Please upload relevant documents first."
            """, question);
    }

    /**
     * Get system prompt for chat completion.
     */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Build a list of messages for OpenAI Chat Completion API.
     *
     * @param question user question
     * @param sources retrieved sources
     * @return formatted messages for API
     */
    public List<ChatMessage> buildChatMessages(String question, List<SourceReference> sources) {
        return List.of(
            new ChatMessage("system", SYSTEM_PROMPT),
            new ChatMessage("user", buildPrompt(question, sources))
        );
    }

    /**
     * Simple record for chat messages.
     */
    public record ChatMessage(String role, String content) {}
}
