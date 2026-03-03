package com.resumebuilder.service;

import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ATS Scoring Service — fully dynamic, zero hardcoded keywords.
 *
 * All keyword intelligence lives in the LLM prompt (resume-tailor-prompt.txt).
 * The LLM extracts JD keywords and self-audits which ones it placed in the resume.
 * This service only does the math on those two lists.
 *
 * Input:
 *   extractedJdKeywords — every keyword the LLM identified from the job description
 *   detectedInResume    — subset the LLM confirmed it placed in the generated resume
 *
 * Output:
 *   atsScore            — integer 0–100 (true match percentage)
 *   scoreLabel          — "Excellent" / "Good" / "Needs Improvement"
 *   matchedKeywords     — keywords present in both lists (hits)
 *   missingKeywords     — keywords in JD but NOT placed in resume (gaps)
 */
@Service
public class AtsScoreService {

    /**
     * Primary scoring path — called after AI resume generation.
     * Uses the two lists returned directly by the LLM.
     */
    public AtsScoreResponse scoreFromLlmOutput(
            List<String> extractedJdKeywords,
            List<String> detectedInResume) {

        AtsScoreResponse response = new AtsScoreResponse();

        if (extractedJdKeywords == null) extractedJdKeywords = new ArrayList<>();
        if (detectedInResume == null)    detectedInResume    = new ArrayList<>();

        // Normalise to lowercase for dedup — display values kept as-is from LLM
        final List<String> detectedLower = detectedInResume.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        // matched = intersection (keywords the LLM placed in resume)
        List<String> matched = extractedJdKeywords.stream()
                .filter(kw -> detectedLower.contains(kw.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        // missing = JD keywords NOT placed in resume
        List<String> missing = extractedJdKeywords.stream()
                .filter(kw -> !detectedLower.contains(kw.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        int total      = extractedJdKeywords.size();
        int matchCount = matched.size();

        // True percentage — no artificial floor/ceiling
        int score = total == 0 ? 0 : (int) Math.round((double) matchCount / total * 100);

        response.setAtsScore(score);
        response.setMatchedSkills(matchCount);
        response.setTotalSkills(total);
        response.setMatchedKeywords(matched);
        response.setMissingKeywords(missing);
        response.setScoreLabel(
                score >= 85 ? "Excellent" :
                        score >= 70 ? "Good"      : "Needs Improvement");

        return response;
    }

    /**
     * Fallback — used by the legacy /ats-score endpoint (profile preview before generation).
     * Delegates to the primary scorer using whatever partial lists are available.
     */
    public AtsScoreResponse calculateScore(
            List<String> profileSkills,
            List<String> jdKeywords) {

        // Treat profile skills as "detectedInResume" for a rough preview score
        return scoreFromLlmOutput(jdKeywords, profileSkills);
    }
}