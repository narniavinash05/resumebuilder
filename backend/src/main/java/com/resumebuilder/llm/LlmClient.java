package com.resumebuilder.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmClient {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LlmClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String callLLM(String prompt) {

        long start = System.currentTimeMillis();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "Return STRICT JSON only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3
        );

        try {
            log.info("📡 Calling LLM API | model={}", model);

            String rawResponse = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            long timeTaken = System.currentTimeMillis() - start;
            log.info("LLM response received in {} ms", timeTaken);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new RuntimeException("Empty response from LLM");
            }

            return extractContent(rawResponse);

        } catch (Exception e) {
            long timeTaken = System.currentTimeMillis() - start;
            log.error("LLM call failed after {} ms", timeTaken, e);
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                log.error("Invalid LLM response structure: {}", rawResponse);
                throw new RuntimeException("Invalid LLM response structure");
            }

            String content = contentNode.asText();

            log.debug("Raw LLM content (truncated): {}",
                    content.substring(0, Math.min(200, content.length())));

            return sanitizeResponse(content);

        } catch (Exception e) {
            log.error("Failed to parse LLM response", e);
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    private String sanitizeResponse(String response) {
        if (response == null) {
            throw new RuntimeException("LLM returned null response");
        }

        return response
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}