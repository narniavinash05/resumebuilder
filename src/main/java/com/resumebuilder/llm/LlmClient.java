package com.resumebuilder.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

    public LlmClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public String callLLM(String prompt) {

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Return STRICT JSON only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3
        );

        try {

            String rawResponse = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = mapper.readTree(rawResponse);

            String content = root
                    .get("choices")
                    .get(0)
                    .get("message")
                    .get("content")
                    .asText();

            return sanitizeResponse(content);

        } catch (Exception e) {
            throw new RuntimeException("LLM call failed", e);
        }
    }

    /**
     * Removes markdown wrappers and trims output.
     */
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