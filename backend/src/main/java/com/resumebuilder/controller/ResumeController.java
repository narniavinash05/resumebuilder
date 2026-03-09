package com.resumebuilder.controller;

import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import com.resumebuilder.model.Resume;
import com.resumebuilder.model.TailorRequest;
import com.resumebuilder.service.AtsScoreService;
import com.resumebuilder.service.ResumePdfService;
import com.resumebuilder.service.ResumeParseService;
import com.resumebuilder.service.ResumeTailoringService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeTailoringService tailoringService;
    private final ResumePdfService       resumePdfService;
    private final AtsScoreService        atsScoreService;
    private final ResumeParseService     resumeParseService;

    public ResumeController(ResumeTailoringService tailoringService,
                            ResumePdfService       resumePdfService,
                            AtsScoreService        atsScoreService,
                            ResumeParseService     resumeParseService) {
        this.tailoringService   = tailoringService;
        this.resumePdfService   = resumePdfService;
        this.atsScoreService    = atsScoreService;
        this.resumeParseService = resumeParseService;
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
    // PRIMARY endpoint — single LLM call handles everything:
    //   1. Extract technical keywords from JD
    //   2. Generate tailored resume (freely enrich skills/experience)
    //   3. Return matchedKeywords, missingKeywords
    //   4. Return holistic atsScore (keyword match 40% + fit 25% + completeness 20% + density 15%)
    //
    // Java does ZERO scoring logic — pure pass-through from LLM judgment.
    @PostMapping("/tailor-generate-score")
    public ResponseEntity<Map<String, Object>> tailorGenerateWithScore(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        // 1. LLM call — resume + full scoring in one shot
        Resume tailoredResume = tailoringService.tailorResume(
                request.getResumeMetaData(), request.getJobDescription());

        // 2. Map LLM scoring onto DTO (zero Java logic, pure pass-through)
        AtsScoreResponse atsScore = atsScoreService.buildFromResume(tailoredResume);

        // 3. Render PDF
        byte[] pdf       = resumePdfService.generateResume(tailoredResume);
        String pdfBase64 = Base64.getEncoder().encodeToString(pdf);

        // 4. Build unified response
        Map<String, Object> result = new HashMap<>();
        result.put("atsScore",          atsScore.getAtsScore());
        result.put("scoreLabel",        atsScore.getScoreLabel());
        result.put("matchedSkills",     atsScore.getMatchedSkills());
        result.put("totalSkills",       atsScore.getTotalSkills());
        result.put("matchedKeywords",   atsScore.getMatchedKeywords());
        result.put("missingKeywords",   atsScore.getMissingKeywords());
        result.put("scoringBreakdown",  tailoredResume.getScoringBreakdown());
        result.put("pdfBase64",         pdfBase64);

        return ResponseEntity.ok(result);
    }

    // ── POST /api/resume/parse ────────────────────────────────────────────────
    // Resume auto-fill feature (Workday-style):
    //   Upload PDF/DOCX/TXT → LLM extracts all sections → returns structured
    //   profile JSON → frontend pre-fills all form fields for review/editing.
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseUploadedResume(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "No file uploaded"));
        }

        Map<String, Object> profileData = resumeParseService.parseResume(file);
        return ResponseEntity.ok(profileData);
    }
}