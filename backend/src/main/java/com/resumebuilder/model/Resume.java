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
     * All meaningful keywords the LLM extracted from the job description.
     * Populated by the AI prompt (Step 1 of the prompt).
     * Excluded from PDF generation — used only for ATS scoring.
     */
    private List<String> extractedJdKeywords;

    /**
     * Subset of extractedJdKeywords that the LLM confirmed it placed
     * in the generated resume (Step 3 self-audit).
     * Excluded from PDF generation — used only for ATS scoring.
     */
    private List<String> detectedInResume;
}