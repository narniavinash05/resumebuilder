package com.resumebuilder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Resume {
    private String fullName;
    private String professionalSummary;
    private ContactInfo contactInfo;
    private List<Experience> experiences;
    private List<Education> education;
    private List<SkillCategory> skillCategories;
    private List<Certification> certifications;

    /**
     * Keywords the LLM extracted from the job description (Step 1 of the prompt).
     * Used by AtsScoreService for deterministic text scanning.
     * Not rendered in the PDF.
     */
    private List<String> extractedJdKeywords;
}