package com.resumebuilder.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class PromptBuilder {

    private static final String PROMPT_PATH =
            "prompts/resume-tailor-prompt.txt";

    private final ObjectMapper objectMapper;

    public PromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildPrompt(Object resumeMetaData,
                              String jobDescription) {

        String template = loadPromptTemplate();

        try {
            String resumeJson =
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(resumeMetaData);

            return template
                    .replace("{{resumeData}}", resumeJson)
                    .replace("{{jobDescription}}", jobDescription);

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize resume metadata", e);
        }
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource =
                    new ClassPathResource(PROMPT_PATH);

            return new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt file", e);
        }
    }
}