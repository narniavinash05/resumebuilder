package com.resumebuilder.llm;

import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    public String buildPrompt(Object resumeMetaData, String jobDescription) {

        return """
        CANDIDATE RESUME:
        %s

        JOB DESCRIPTION:
        %s

        TASK:
        1. Tailor resume to job description.
        2. Do not fabricate experience.
        3. Optimize keywords for ATS.
        4. Return structured JSON only.

        Expected Output Format:
        {
          "summary": "",
          "experience": [],
          "skills": []
        }
        """.formatted(resumeMetaData, jobDescription);
    }
}
