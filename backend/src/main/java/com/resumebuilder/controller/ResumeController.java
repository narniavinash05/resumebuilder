package com.resumebuilder.controller;

import com.resumebuilder.dto.AuthDtos.AtsScoreResponse;
import com.resumebuilder.model.Resume;
import com.resumebuilder.model.TailorRequest;
import com.resumebuilder.service.*;
import lombok.extern.slf4j.Slf4j;
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


@Slf4j
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeTailoringService  tailoringService;
    private final ResumePdfService        resumePdfService;
    private final ResumeDocxService       resumeDocxService;   // ← NEW
    private final AtsScoreService         atsScoreService;
    private final ResumeParserService     resumeParserService;
    private final ResumeVersionService    resumeVersionService;

    public ResumeController(ResumeTailoringService tailoringService,
                            ResumePdfService       resumePdfService,
                            ResumeDocxService      resumeDocxService,   // ← NEW
                            AtsScoreService        atsScoreService,
                            ResumeParserService    resumeParserService,
                            ResumeVersionService   resumeVersionService) {
        this.tailoringService    = tailoringService;
        this.resumePdfService    = resumePdfService;
        this.resumeDocxService   = resumeDocxService;           // ← NEW
        this.atsScoreService     = atsScoreService;
        this.resumeParserService = resumeParserService;
        this.resumeVersionService = resumeVersionService;
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

    // ── POST /api/resume/tailor-generate-score ────────────────────────────────
    // PRIMARY endpoint — single LLM call handles everything.
    // Now also returns docxBase64 alongside pdfBase64.
    @PostMapping("/tailor-generate-score")
    public ResponseEntity<Map<String, Object>> tailorGenerateWithScore(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        // 1. LLM call — resume + full scoring in one shot
        Resume tailoredResume = tailoringService.tailorResume(
                request.getResumeMetaData(), request.getJobDescription());

        resumeVersionService.saveResumeVersion(
                userDetails.getUsername(),
                request.getJobDescription(),
                tailoredResume);

        // 2. Map LLM scoring onto DTO
        AtsScoreResponse atsScore = atsScoreService.buildFromResume(tailoredResume);

        // 3. Render PDF
        byte[] pdf       = resumePdfService.generateResume(tailoredResume);
        String pdfBase64 = Base64.getEncoder().encodeToString(pdf);

        // 4. Render DOCX  ← NEW
        byte[] docx       = resumeDocxService.generateResume(tailoredResume);
        String docxBase64 = Base64.getEncoder().encodeToString(docx);

        // 5. Build unified response
        Map<String, Object> result = new HashMap<>();
        result.put("atsScore",         atsScore.getAtsScore());
        result.put("scoreLabel",       atsScore.getScoreLabel());
        result.put("matchedSkills",    atsScore.getMatchedSkills());
        result.put("totalSkills",      atsScore.getTotalSkills());
        result.put("matchedKeywords",  atsScore.getMatchedKeywords());
        result.put("missingKeywords",  atsScore.getMissingKeywords());
        result.put("scoringBreakdown", tailoredResume.getScoringBreakdown());
        result.put("pdfBase64",        pdfBase64);
        result.put("docxBase64",       docxBase64);   // ← NEW

        return ResponseEntity.ok(result);
    }

    // ── POST /api/resume/parse ────────────────────────────────────────────────
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseUploadedResume(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        log.info("/parse API called");

        try {
            if (file == null || file.isEmpty()) {
                log.warn("Empty file received");
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No file uploaded"));
            }

            log.info("📄 File received: name={}, size={} bytes, type={}",
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType());

            Map<String, Object> profileData = resumeParserService.parseResume(file);

            log.info("Resume parsed successfully");

            return ResponseEntity.ok(profileData);

        } catch (Exception e) {
            log.error("Error parsing resume", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}