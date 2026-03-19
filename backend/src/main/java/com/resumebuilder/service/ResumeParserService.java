package com.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * LLM-powered resume parser.
 * <p>
 * Flow:
 * 1. Extract raw text from the uploaded file (PDF, DOCX, DOC, TXT)
 * 2. Send raw text to the LLM with a structured extraction prompt
 * 3. LLM returns profile JSON → passed back to frontend for review/edit before saving
 * <p>
 * This replaces the previous regex/heuristic-based parser with an LLM call,
 * dramatically improving accuracy for varied resume formats.
 */
@Slf4j
@Service
public class ResumeParserService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ResumeParserService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    public Map<String, Object> parseResume(MultipartFile file) throws Exception {
        log.info("Starting resume parsing");
        String rawText = extractText(file);
        log.info("Extracted text length: {}", rawText.length());
        String prompt = buildExtractionPrompt(rawText);
        log.info("Sending to LLM...");
        String llmResponse = llmClient.callLLM(prompt);
        log.info("LLM response received");
        return objectMapper.readValue(llmResponse, Map.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEXT EXTRACTION  (PDF / DOCX / DOC / TXT)
    // ═══════════════════════════════════════════════════════════════════════

    private String extractText(MultipartFile file) throws Exception {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        String mime = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase();

        if (name.endsWith(".pdf") || mime.contains("pdf")) {
            return extractPdf(file.getBytes());
        } else if (name.endsWith(".docx") || mime.contains("wordprocessingml")) {
            return extractDocx(file.getInputStream());
        } else if (name.endsWith(".doc") || mime.contains("msword")) {
            return extractDoc(file.getInputStream());
        } else {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Fast path first — position sort is 4-8× slower
            stripper.setSortByPosition(false);
            String text = stripper.getText(doc);
            // Heuristic: if output looks garbled, re-extract with positional sort
            if (looksGarbled(text)) {
                stripper.setSortByPosition(true);
                text = stripper.getText(doc);
            }
            return text;
        }
    }

    private boolean looksGarbled(String text) {
        if (text == null || text.length() < 300) return false;
        String[] lines = text.split("\n");
        int shortCount = 0, total = 0;
        for (String ln : lines) {
            String t = ln.trim();
            if (t.isEmpty()) continue;
            total++;
            if (t.length() < 15 && !t.matches(".*\\d{4}.*") && t.split("\\s+").length <= 2)
                shortCount++;
        }
        return total > 15 && ((double) shortCount / total) > 0.32;
    }

    private String extractDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private String extractDoc(InputStream is) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(is);
             WordExtractor ex = new WordExtractor(doc)) {
            return ex.getText();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LLM EXTRACTION PROMPT
    // ═══════════════════════════════════════════════════════════════════════

    private String buildExtractionPrompt(String rawText) {
        return """
                You are a precise resume parser. Extract all information from the resume text below and return it
                as a single strict JSON object matching the schema shown. Fill every field you can find.
                Return ONLY raw JSON — no markdown fences, no explanations, no commentary.
                
                RESUME TEXT:
                """ + rawText + """
                
                OUTPUT SCHEMA:
                {
                  "name": "full name",
                  "headline": "job title or professional headline",
                  "email": "email address",
                  "phone": "phone number",
                  "location": "city, state or country",
                  "linkedin": "linkedin URL or blank",
                  "github": "github URL or portfolio URL or blank",
                  "website": "personal website URL or blank",
                  "summary": "professional summary paragraph or blank",
                  "companies": [
                    {
                      "company": "company name",
                      "role": "job title",
                      "location": "city, state or Remote",
                      "startDate": "YYYY-MM",
                      "endDate": "YYYY-MM or blank if current",
                      "current": false,
                      "description": "bullet points joined with newlines, each starting with a bullet character"
                    }
                  ],
                  "education": [
                    {
                      "institution": "university or school name",
                      "degree": "degree type e.g. Bachelor of Science",
                      "field": "field of study e.g. Computer Science",
                      "location": "city, state or blank",
                      "year": "graduation year as 4-digit string",
                      "gpa": "GPA if present else blank"
                    }
                  ],
                  "skills": ["skill1", "skill2"],
                  "certifications": [
                    {
                      "name": "certification name",
                      "issuer": "issuing organization",
                      "year": "year obtained"
                    }
                  ]
                }
                
                RULES:
                - Extract EVERY work experience entry found, ordered oldest to newest
                - skills[] must be a flat list of individual skill names with no category labels
                - Dates must be YYYY-MM format; if only a year is found use YYYY-01
                - For current roles set "current": true and leave "endDate" as blank string
                - If a field is not present in the resume, return a blank string or empty array
                - description should preserve all bullet points from the original resume
                - Return raw JSON only — no markdown, no preamble, no explanation
                """;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Strips markdown code fences that some LLMs occasionally add
     * even when instructed not to.
     */
    private String sanitize(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        // Remove ```json ... ``` or ``` ... ```
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.trim();
    }
}