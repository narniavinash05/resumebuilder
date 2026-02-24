package com.resumebuilder.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

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

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a professional resume optimizer. Return JSON only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3
        );

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> json.get("choices").get(0)
                        .get("message").get("content").asText())
                .block();
    }
}
