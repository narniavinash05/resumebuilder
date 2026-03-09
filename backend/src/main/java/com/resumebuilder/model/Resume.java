package com.resumebuilder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Represents the full LLM response — resume content + ATS scoring data.
 *
 * The LLM now owns ALL scoring logic:
 *   - extractedKeywords   : technical keywords it found in the JD
 *   - matchedKeywords     : those keywords it placed in the generated resume
 *   - missingKeywords     : keywords it could not defensibly place
 *   - atsScore            : holistic 0-100 score based on keyword match + fit + format
 *   - scoreLabel          : human label for the score
 *   - scoringBreakdown    : per-dimension scores for transparency
 *
 * Resume content fields (fullName, experiences, etc.) are rendered into the PDF.
 * Scoring fields are returned to the frontend and never rendered in the PDF.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Resume {

    // ── Resume content (rendered in PDF) ──────────────────────────────────────
    private String fullName;
    private String professionalSummary;
    private ContactInfo contactInfo;
    private List<Experience> experiences;
    private List<Education> education;
    private List<SkillCategory> skillCategories;
    private List<Certification> certifications;

    // ── ATS scoring (returned to frontend, NOT rendered in PDF) ───────────────
    private List<String> extractedKeywords;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private int atsScore;
    private String scoreLabel;
    private ScoringBreakdown scoringBreakdown;

    // ── Scoring breakdown inner class ─────────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoringBreakdown {
        private int keywordMatch;       // 0-100 (weighted 40%)
        private int candidateFit;       // 0-100 (weighted 25%)
        private int resumeCompleteness; // 0-100 (weighted 20%)
        private int keywordDensity;     // 0-100 (weighted 15%)
        private String notes;           // one-sentence explanation
    }
}