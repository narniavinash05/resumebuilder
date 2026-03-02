package com.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.llm.LlmClient;
import com.resumebuilder.llm.PromptBuilder;
import com.resumebuilder.model.Resume;
import org.springframework.stereotype.Service;

@Service
public class ResumeTailoringService {

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
        String llmResponse = llmClient.callLLM(prompt);
        return objectMapper.readValue(llmResponse, Resume.class);
    }
}
