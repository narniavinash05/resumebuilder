package com.resumebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ATS Scoring Service — fully dynamic, zero hardcoded keyword lists.
 *
 * Flow:
 *   1. LLM extracts technical keywords from the JD  →  extractedJdKeywords[]
 *   2. Java filters out any vague/generic terms that slipped through (structural rules only)
 *   3. Java flattens the resume JSON to plain text
 *   4. Java checks each surviving keyword against the resume text (deterministic)
 *   5. Score = matched / total * 100
 */
@Service
public class AtsScoreService {

    private final ObjectMapper objectMapper;

    // ── Vague single-word descriptors that are NEVER valid ATS keywords ───────
    // This list is intentionally small — it only covers unambiguous quality/
    // adjective words that the LLM occasionally leaks through despite prompt rules.
    // It does NOT try to enumerate technical terms (that's the prompt's job).
    private static final Set<String> GENERIC_SINGLES = Set.of(
            "high-quality", "scalable", "reliable", "robust", "modern", "clean",
            "innovative", "collaborative", "motivated", "passionate", "dynamic",
            "detail-oriented", "results-driven", "self-starter", "fast-learner",
            "proactive", "dedicated", "hardworking", "organized", "adaptable",
            "efficient", "effective", "excellent", "outstanding", "exceptional",
            "strong", "solid", "good", "great", "best", "top", "leading",
            "experienced", "skilled", "talented", "qualified", "proven",
            "ability", "capable", "eager", "enthusiastic", "creative"
    );

    // ── Vague multi-word phrases that are never ATS-scannable ────────────────
    private static final Set<String> GENERIC_PHRASES = Set.of(
            "cross-functional collaboration", "execution speed", "platform flexibility",
            "high-quality code", "modern software development practices",
            "iterative development", "iterative value", "best practices",
            "creative solutions", "innovative thinking", "fast-paced environment",
            "duties as assigned", "other responsibilities", "stakeholder management",
            "strong communication", "strong communication skills", "communication skills",
            "team player", "fast learner", "attention to detail", "critical thinking",
            "analytical skills", "interpersonal skills", "time management",
            "problem solving", "problem-solving skills", "decision making",
            "leadership skills", "organizational skills", "multitasking",
            "open communication", "technical culture", "real-world constraints",
            "high-impact projects", "delivery excellence", "quality code",
            "full-stack experience", "full stack experience", "software engineering experience",
            "professional software engineering experience", "years of experience",
            "years of professional experience"
    );

    public AtsScoreService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIMARY — called by ResumeController after AI generation.
    //
    // @param rawKeywords  raw extractedJdKeywords list from the LLM
    // @param resumeJson   full generated resume as a JSON string
    // ─────────────────────────────────────────────────────────────────────────
    public AtsScoreResponse scoreFromResumeJson(
            List<String> rawKeywords,
            String resumeJson) {

        AtsScoreResponse response = new AtsScoreResponse();

        if (rawKeywords == null || rawKeywords.isEmpty()) {
            response.setAtsScore(0);
            response.setScoreLabel("Needs Improvement");
            response.setMatchedKeywords(new ArrayList<>());
            response.setMissingKeywords(new ArrayList<>());
            response.setTotalSkills(0);
            response.setMatchedSkills(0);
            return response;
        }

        // Step 1 — Filter: keep only technical/role-specific keywords
        List<String> technicalKeywords = rawKeywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .filter(this::isTechnicalKeyword)
                .collect(Collectors.toList());

        // Step 2 — Flatten the entire resume JSON into one lowercase text blob
        String resumeText = flattenResumeToText(resumeJson).toLowerCase();

        // Step 3 — Deterministic scan: is each keyword present in the resume text?
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String keyword : technicalKeywords) {
            if (resumeText.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        int total      = technicalKeywords.size();
        int matchCount = matched.size();
        int score      = total == 0 ? 0 : (int) Math.round((double) matchCount / total * 100);

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

    // ─────────────────────────────────────────────────────────────────────────
    // Filter — returns true if the keyword is a genuine technical/role-specific
    // term worth scoring against. Uses structural rules only — no hardcoded
    // technology lists. The LLM is responsible for knowing technologies;
    // this filter only removes structurally obvious non-technical terms.
    // ─────────────────────────────────────────────────────────────────────────
    private boolean isTechnicalKeyword(String keyword) {
        String lower = keyword.toLowerCase().trim();

        // 1. Reject known generic single-words and phrases
        if (GENERIC_SINGLES.contains(lower)) return false;
        if (GENERIC_PHRASES.contains(lower))  return false;

        // 2. Reject if too short (single chars, e.g. stray punctuation)
        if (keyword.trim().length() < 2) return false;

        // 3. Structural signals that indicate a TECHNICAL keyword:
        //    - Contains a digit          → Java 11, Python 3, S3, EC2, OAuth2, Java 17
        //    - Contains a dot            → Vue.js, React.js, Node.js, .NET
        //    - Contains a slash          → CI/CD, TCP/IP
        //    - Contains a plus           → C++, g++
        //    - Contains a hash           → C#, F#
        //    - All caps or mixed caps    → AWS, SQL, GCP, REST, API, DBT, ETL, SPA
        //    - Known prefix pattern      → starts with capital (proper noun / product name)
        boolean hasDigit       = keyword.matches(".*\\d.*");
        boolean hasDot         = keyword.contains(".");
        boolean hasSlash       = keyword.contains("/");
        boolean hasSpecial     = keyword.contains("+") || keyword.contains("#");
        boolean hasUpperInside = !keyword.equals(keyword.toLowerCase())
                && !keyword.equals(keyword.toUpperCase());
        boolean isAcronymStyle = keyword.equals(keyword.toUpperCase()) && keyword.length() >= 2;
        boolean startsWithCap  = Character.isUpperCase(keyword.charAt(0));

        if (hasDigit || hasDot || hasSlash || hasSpecial
                || hasUpperInside || isAcronymStyle || startsWithCap) {
            return true;
        }

        // 4. Multi-word check: if it's multiple words, allow it only if it contains
        //    at least one capitalised word (indicating a named technology or standard)
        //    e.g. "Spring Boot", "RESTful APIs", "full SDLC", "event-driven architecture"
        String[] words = keyword.split("\\s+");
        if (words.length > 1) {
            for (String word : words) {
                if (!word.isEmpty() && (Character.isUpperCase(word.charAt(0))
                        || word.equals(word.toUpperCase()))) {
                    return true;  // at least one proper-noun/acronym word
                }
            }
            // Multi-word phrase that is all lowercase → likely a generic description
            // e.g. "iterative development", "execution speed", "cross-functional collaboration"
            return false;
        }

        // 5. Single all-lowercase word — likely generic unless it's a known
        //    lowercase technology (python, java, etc.). Let it through since
        //    the LLM was instructed to preserve original JD casing, so a
        //    technology appearing in all-lowercase in the JD is still valid.
        //    But apply a minimum length to filter noise words.
        return keyword.trim().length() >= 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Walk the entire resume JSON tree and concatenate every string value.
    // Skips field names (keys) — only collects actual content values.
    // ─────────────────────────────────────────────────────────────────────────
    private String flattenResumeToText(String resumeJson) {
        try {
            JsonNode root = objectMapper.readTree(resumeJson);
            StringBuilder sb = new StringBuilder();
            collectTextValues(root, sb);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void collectTextValues(JsonNode node, StringBuilder sb) {
        if (node == null) return;
        if (node.isTextual()) {
            sb.append(" ").append(node.asText());
        } else if (node.isArray()) {
            node.forEach(child -> collectTextValues(child, sb));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectTextValues(entry.getValue(), sb));
        }
    }
}