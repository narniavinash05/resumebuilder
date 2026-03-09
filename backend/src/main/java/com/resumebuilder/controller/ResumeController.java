package com.resumebuilder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import com.resumebuilder.model.Resume;
import com.resumebuilder.model.TailorRequest;
import com.resumebuilder.service.AtsScoreService;
import com.resumebuilder.service.ResumePdfService;
import com.resumebuilder.service.ResumeTailoringService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumePdfService       resumePdfService;
    private final ResumeTailoringService tailoringService;
    private final AtsScoreService        atsScoreService;
    private final ObjectMapper           objectMapper;

    public ResumeController(ResumeTailoringService tailoringService,
                            ResumePdfService       resumePdfService,
                            AtsScoreService        atsScoreService,
                            ObjectMapper           objectMapper) {
        this.tailoringService = tailoringService;
        this.resumePdfService = resumePdfService;
        this.atsScoreService  = atsScoreService;
        this.objectMapper     = objectMapper;
    }

    // ── POST /api/resume/generate ─────────────────────────────────────────────
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody Resume resume) throws Exception {
        byte[] pdf = resumePdfService.generateResume(resume);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── POST /api/resume/tailor-and-generate ──────────────────────────────────
    @PostMapping("/tailor-and-generate")
    public ResponseEntity<byte[]> tailorAndGenerate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        Resume resume = tailoringService.tailorResume(
                request.getResumeMetaData(), request.getJobDescription());

        byte[] pdf = resumePdfService.generateResume(resume);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tailored_resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── POST /api/resume/tailor-generate-score ────────────────────────────────
    // PRIMARY endpoint.
    //
    // Step 1 (LLM)  : extract JD keywords + generate tailored resume JSON
    // Step 2 (Java) : flatten resume JSON → scan for each keyword (deterministic)
    // Step 3 (Java) : compute score = matched / total * 100
    //
    // Response: { atsScore, scoreLabel, matchedSkills, totalSkills,
    //             matchedKeywords, missingKeywords, pdfBase64 }
    @PostMapping("/tailor-generate-score")
    public ResponseEntity<Map<String, Object>> tailorGenerateWithScore(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        // 1. LLM generates resume + returns extractedJdKeywords[]
        Resume tailoredResume = tailoringService.tailorResume(
                request.getResumeMetaData(), request.getJobDescription());

        // 2. Serialize resume to JSON so AtsScoreService can scan its full text
        String resumeJson = objectMapper.writeValueAsString(tailoredResume);

        // 3. Get the LLM-extracted keyword list
        java.util.List<String> extractedJdKeywords =
                tailoredResume.getExtractedJdKeywords() != null
                        ? tailoredResume.getExtractedJdKeywords()
                        : new ArrayList<>();

        // 4. Deterministic scan: check each keyword against the resume text
        AtsScoreResponse atsScore = atsScoreService.scoreFromResumeJson(
                extractedJdKeywords, resumeJson);

        // 5. Render PDF
        byte[] pdf       = resumePdfService.generateResume(tailoredResume);
        String pdfBase64 = Base64.getEncoder().encodeToString(pdf);

        // 6. Return combined response
        Map<String, Object> result = new HashMap<>();
        result.put("atsScore",        atsScore.getAtsScore());
        result.put("scoreLabel",      atsScore.getScoreLabel());
        result.put("matchedSkills",   atsScore.getMatchedSkills());
        result.put("totalSkills",     atsScore.getTotalSkills());
        result.put("matchedKeywords", atsScore.getMatchedKeywords());
        result.put("missingKeywords", atsScore.getMissingKeywords());
        result.put("pdfBase64",       pdfBase64);

        return ResponseEntity.ok(result);
    }
}