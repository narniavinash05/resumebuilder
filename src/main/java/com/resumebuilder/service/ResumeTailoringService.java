package com.resumebuilder.service;

import com.resumebuilder.llm.LlmClient;
import com.resumebuilder.llm.PromptBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.model.Resume;

@Service
public class ResumeTailoringService {

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeTailoringService(LlmClient llmClient,
                                  PromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    public Resume tailorResume(Object resumeMetaData,
                               String jobDescription) throws Exception {

        String prompt = promptBuilder.buildPrompt(
                resumeMetaData,
                jobDescription
        );

        String llmResponse = llmClient.callLLM(prompt);

        return objectMapper.readValue(llmResponse, Resume.class);
    }
}
