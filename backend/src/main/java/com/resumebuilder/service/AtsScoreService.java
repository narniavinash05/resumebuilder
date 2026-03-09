package com.resumebuilder.service;

import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import com.resumebuilder.model.Resume;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ATS Scoring Service — pure pass-through.
 *
 * All scoring intelligence lives entirely in the LLM prompt.
 * The LLM determines:
 *   - which keywords are technical and worth scoring
 *   - which of those it placed in the generated resume
 *   - the holistic ATS score (keyword match + candidate fit + format + density)
 *
 * This service only maps the LLM's output onto the AtsScoreResponse DTO.
 * Zero keyword filtering. Zero string matching. Zero hardcoded logic.
 */
@Service
public class AtsScoreService {

    /**
     * Builds the ATS score response directly from the LLM-generated Resume object.
     * The LLM has already done all the scoring work — this just extracts it.
     */
    public AtsScoreResponse buildFromResume(Resume resume) {
        AtsScoreResponse response = new AtsScoreResponse();

        List<String> matched = safe(resume.getMatchedKeywords());
        List<String> missing = safe(resume.getMissingKeywords());
        List<String> extracted = safe(resume.getExtractedKeywords());

        response.setAtsScore(resume.getAtsScore());
        response.setScoreLabel(resolveLabel(resume.getAtsScore(), resume.getScoreLabel()));
        response.setMatchedKeywords(matched);
        response.setMissingKeywords(missing);
        response.setMatchedSkills(matched.size());
        response.setTotalSkills(extracted.size());

        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> safe(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }

    /**
     * Fallback label resolution in case the LLM returns a blank scoreLabel.
     * Mirrors the scoring bands defined in the prompt.
     */
    private String resolveLabel(int score, String llmLabel) {
        if (llmLabel != null && !llmLabel.isBlank()) return llmLabel;
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good";
        if (score >= 55) return "Fair";
        return "Needs Improvement";
    }
}