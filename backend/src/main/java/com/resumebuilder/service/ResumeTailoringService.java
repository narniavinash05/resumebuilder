package com.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.llm.LlmClient;
import com.resumebuilder.llm.PromptBuilder;
import com.resumebuilder.model.Resume;
import org.springframework.stereotype.Service;

@Service
public class ResumeTailoringService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 600;

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ResumeTailoringService(LlmClient llmClient,
                                  PromptBuilder promptBuilder,
                                  ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public Resume tailorResume(Object resumeMetaData, String jobDescription) throws Exception {

        String prompt = promptBuilder.buildPrompt(resumeMetaData, jobDescription);

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_ATTEMPTS) {
            try {
                attempt++;

                String llmResponse = llmClient.callLLM(prompt);

                // Optional cleanup if model wraps JSON in ```json ```
                llmResponse = sanitizeResponse(llmResponse);

                return objectMapper.readValue(llmResponse, Resume.class);

            } catch (Exception ex) {
                lastException = ex;

                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }

                // Small backoff before retry
                try {
                    Thread.sleep(BACKOFF_MS * attempt);
                } catch (InterruptedException ignored) {}
            }
        }

        throw new RuntimeException(
                "Failed to generate valid resume after " + MAX_ATTEMPTS + " attempts",
                lastException
        );
    }

    /**
     * Removes common LLM formatting artifacts like markdown fences.
     */
    private String sanitizeResponse(String response) {
        if (response == null) return null;

        return response
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }
}