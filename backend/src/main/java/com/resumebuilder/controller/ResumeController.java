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
    // Generate PDF from raw Resume JSON (no AI)
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody Resume resume) throws Exception {
        byte[] pdf = resumePdfService.generateResume(resume);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── POST /api/resume/tailor-and-generate ──────────────────────────────────
    // Legacy: AI tailor + download raw PDF (no score)
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
    // PRIMARY endpoint: AI tailors resume + self-audits keyword coverage.
    // ATS score is computed purely from the LLM's two keyword lists —
    // no hardcoded patterns, no string matching, no stop-word lists.
    //
    // Response JSON:
    // {
    //   atsScore         : int       (0–100, true percentage)
    //   scoreLabel       : string    ("Excellent" | "Good" | "Needs Improvement")
    //   matchedSkills    : int       (count of keywords placed in resume)
    //   totalSkills      : int       (total JD keywords extracted)
    //   matchedKeywords  : string[]  (keywords confirmed in resume — green chips)
    //   missingKeywords  : string[]  (keywords missing from resume — red chips)
    //   pdfBase64        : string    (base64-encoded PDF)
    // }
    @PostMapping("/tailor-generate-score")
    public ResponseEntity<Map<String, Object>> tailorGenerateWithScore(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        // 1. LLM: extract JD keywords + generate resume + self-audit coverage
        Resume tailoredResume = tailoringService.tailorResume(
                request.getResumeMetaData(), request.getJobDescription());

        // 2. Pull the two keyword lists directly from the LLM's output
        //    (populated by extractedJdKeywords and detectedInResume fields in Resume model)
        java.util.List<String> extractedJdKeywords =
                tailoredResume.getExtractedJdKeywords() != null
                        ? tailoredResume.getExtractedJdKeywords()
                        : new ArrayList<>();

        java.util.List<String> detectedInResume =
                tailoredResume.getDetectedInResume() != null
                        ? tailoredResume.getDetectedInResume()
                        : new ArrayList<>();

        // 3. Pure-math scoring — no heuristics, no pattern matching
        AtsScoreResponse atsScore = atsScoreService.scoreFromLlmOutput(
                extractedJdKeywords, detectedInResume);

        // 4. Generate PDF (extractedJdKeywords and detectedInResume are ignored by PDF renderer
        //    because ResumePdfService only reads the standard resume fields)
        byte[] pdf       = resumePdfService.generateResume(tailoredResume);
        String pdfBase64 = Base64.getEncoder().encodeToString(pdf);

        // 5. Return everything to the frontend in one response
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