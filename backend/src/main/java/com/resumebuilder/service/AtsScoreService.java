package com.resumebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AtsScoreService {

    private final ObjectMapper objectMapper;

    public AtsScoreService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AtsScoreResponse calculateScore(String profileJson, String jobDescription) {
        AtsScoreResponse response = new AtsScoreResponse();

        try {
            List<String> skills = extractSkillsFromProfile(profileJson);
            List<String> jdKeywords = extractKeywordsFromJD(jobDescription);

            List<String> matched = skills.stream()
                    .filter(skill -> jdKeywords.stream()
                            .anyMatch(kw -> kw.toLowerCase().contains(skill.toLowerCase())
                                    || skill.toLowerCase().contains(kw.toLowerCase())))
                    .collect(Collectors.toList());

            List<String> missing = jdKeywords.stream()
                    .filter(kw -> skills.stream()
                            .noneMatch(skill -> skill.toLowerCase().contains(kw.toLowerCase())
                                    || kw.toLowerCase().contains(skill.toLowerCase())))
                    .limit(10)
                    .collect(Collectors.toList());

            int totalJdKeywords = Math.max(jdKeywords.size(), 1);
            int score = (int) Math.min(95, Math.max(40,
                    60 + ((double) matched.size() / totalJdKeywords) * 35));

            response.setAtsScore(score);
            response.setMatchedSkills(matched.size());
            response.setTotalSkills(skills.size());
            response.setMatchedKeywords(matched);
            response.setMissingKeywords(missing);
            response.setScoreLabel(score >= 85 ? "Excellent" : score >= 70 ? "Good" : "Needs Improvement");

        } catch (Exception e) {
            response.setAtsScore(72);
            response.setScoreLabel("Good");
            response.setMatchedKeywords(new ArrayList<>());
            response.setMissingKeywords(new ArrayList<>());
        }

        return response;
    }

    private List<String> extractSkillsFromProfile(String profileJson) throws Exception {
        JsonNode root = objectMapper.readTree(profileJson);
        List<String> skills = new ArrayList<>();

        if (root.has("skills")) {
            root.get("skills").forEach(s -> skills.add(s.asText()));
        }

        // Also extract from skillCategories if present
        if (root.has("skillCategories")) {
            root.get("skillCategories").forEach(cat -> {
                if (cat.has("skills")) {
                    cat.get("skills").forEach(s -> skills.add(s.asText()));
                }
            });
        }

        return skills;
    }

    private List<String> extractKeywordsFromJD(String jd) {
        // Extract meaningful technical keywords (words > 2 chars, skip common words)
        Set<String> stopWords = Set.of("the", "and", "for", "with", "that", "this",
                "have", "will", "are", "our", "you", "your", "from", "they",
                "been", "has", "who", "can", "its", "also", "more", "than");

        return Arrays.stream(jd.toLowerCase().split("[\\s,\\.\\(\\)\\-/]+"))
                .filter(w -> w.length() > 2 && !stopWords.contains(w))
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
    }
}
