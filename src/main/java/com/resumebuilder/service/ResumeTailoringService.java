package com.resumebuilder.service;

import com.resumebuilder.llm.LlmClient;
import com.resumebuilder.llm.PromptBuilder;
import org.springframework.stereotype.Service;

@Service
public class ResumeTailoringService {

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;

    public ResumeTailoringService(LlmClient llmClient,
                                  PromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    public String tailorResume(Object resumeMetaData,
                               String jobDescription) {

        String prompt = promptBuilder.buildPrompt(
                resumeMetaData,
                jobDescription
        );

        return llmClient.callLLM(prompt);
    }
}
