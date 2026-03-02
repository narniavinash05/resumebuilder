package com.resumebuilder.controller;

import com.resumebuilder.model.Resume;
import com.resumebuilder.model.TailorRequest;
import com.resumebuilder.service.ResumePdfService;
import com.resumebuilder.service.ResumeTailoringService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumePdfService resumePdfService;
    private final ResumeTailoringService tailoringService;

    public ResumeController(ResumeTailoringService tailoringService, ResumePdfService resumePdfService) {
        this.tailoringService = tailoringService;
        this.resumePdfService = resumePdfService;
    }

    // ── POST /api/resume/generate ────────────────────────────────────────
    // Generate PDF from raw Resume JSON (no AI)
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody Resume resume) throws Exception {
        byte[] pdf = resumePdfService.generateResume(resume);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── POST /api/resume/tailor-and-generate ────────────────────────────
    // AI tailor + generate PDF — main endpoint used by frontend
    @PostMapping("/tailor-and-generate")
    public ResponseEntity<byte[]> tailorAndGenerate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TailorRequest request) throws Exception {

        Resume tailoredResume = tailoringService.tailorResume(
                request.getResumeMetaData(),
                request.getJobDescription()
        );

        byte[] pdf = resumePdfService.generateResume(tailoredResume);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tailored_resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
