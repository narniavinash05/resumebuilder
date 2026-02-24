package com.resumebuilder.llm;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class PromptBuilder {

    private static final String PROMPT_PATH =
            "prompts/resume-tailor-prompt.txt";

    public String buildPrompt(Object resumeMetaData,
                              String jobDescription) {

        String template = loadPromptTemplate();

        return template
                .replace("{{resumeData}}", resumeMetaData.toString())
                .replace("{{jobDescription}}", jobDescription);
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