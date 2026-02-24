package com.resumebuilder.controller;


import com.resumebuilder.model.Resume;
import com.resumebuilder.model.TailorRequest;
import com.resumebuilder.service.ResumeTailoringService;
import org.springframework.web.bind.annotation.*;
import com.resumebuilder.service.ResumePdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumePdfService resumePdfService;

    private final ResumeTailoringService tailoringService;

    public ResumeController(ResumeTailoringService tailoringService, ResumePdfService resumePdfService) {
        this.tailoringService = tailoringService;
        this.resumePdfService = resumePdfService;
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody Resume resume) throws Exception {

        byte[] pdf = resumePdfService.generateResume(resume);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/tailor")
    public ResponseEntity<?> tailor(@RequestBody TailorRequest request) {

        String result = tailoringService.tailorResume(
                request.getResumeMetaData(),
                request.getJobDescription()
        );

        return ResponseEntity.ok(result);
    }

}

