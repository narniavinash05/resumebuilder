package com.resumebuilder.controller;


import com.resumebuilder.model.Resume;
import org.springframework.web.bind.annotation.*;
import com.resumebuilder.service.ResumePdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumePdfService resumePdfService;

    public ResumeController(ResumePdfService resumePdfService) {
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

}

