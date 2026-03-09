package com.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.llm.LlmClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Parses an uploaded resume file (PDF, DOCX, TXT) into structured profile JSON
 * that maps directly onto the frontend profile form fields.
 *
 * Flow:
 *   1. Extract raw text from the file (PDF via Apache PDFBox, DOCX via Apache POI, TXT direct)
 *   2. Send raw text to the LLM with a structured extraction prompt
 *   3. LLM returns profile JSON → passed back to frontend for review/edit before saving
 *
 * This is the "Workday-style auto-fill" feature: upload once, review, save.
 */
@Service
public class ResumeParseService {

    private final LlmClient     llmClient;
    private final ObjectMapper  objectMapper;

    public ResumeParseService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient    = llmClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parseResume(MultipartFile file) throws Exception {
        String rawText = extractText(file);
        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("Could not extract text from the uploaded file.");
        }

        String prompt = buildExtractionPrompt(rawText);
        String llmResponse = llmClient.callLLM(prompt);
        llmResponse = sanitize(llmResponse);

        // Parse into a generic map — frontend accepts this directly to pre-fill form fields
        return objectMapper.readValue(llmResponse, Map.class);
    }

    // ── Text extraction ───────────────────────────────────────────────────────

    private String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase() : "";

        if (filename.endsWith(".pdf") || contentType.contains("pdf")) {
            return extractFromPdf(file);
        } else if (filename.endsWith(".docx") || contentType.contains("wordprocessingml")) {
            return extractFromDocx(file);
        } else if (filename.endsWith(".doc") || contentType.contains("msword")) {
            return extractFromDoc(file);
        } else {
            // Treat as plain text
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractFromPdf(MultipartFile file) throws Exception {
        try {
            // Apache PDFBox — must be in pom.xml: org.apache.pdfbox:pdfbox:3.0.x
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Object doc = loaderClass
                    .getMethod("loadPDF", byte[].class)
                    .invoke(null, (Object) file.getBytes());

            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            String text = (String) stripperClass.getMethod("getText", Class.forName("org.apache.pdfbox.pdmodel.PDDocument"))
                    .invoke(stripper, doc);

            doc.getClass().getMethod("close").invoke(doc);
            return text;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Apache PDFBox is not on the classpath. Add org.apache.pdfbox:pdfbox:3.0.x to pom.xml", e);
        }
    }

    private String extractFromDocx(MultipartFile file) throws Exception {
        try {
            // Apache POI — must be in pom.xml: org.apache.poi:poi-ooxml:5.x
            Class<?> docxClass = Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument");
            Object doc = docxClass.getConstructor(java.io.InputStream.class)
                    .newInstance(file.getInputStream());

            Class<?> extractorClass = Class.forName("org.apache.poi.xwpf.extractor.XWPFWordExtractor");
            Object extractor = extractorClass.getConstructor(docxClass).newInstance(doc);
            String text = (String) extractorClass.getMethod("getText").invoke(extractor);

            extractor.getClass().getMethod("close").invoke(extractor);
            return text;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Apache POI is not on the classpath. Add org.apache.poi:poi-ooxml:5.x to pom.xml", e);
        }
    }

    private String extractFromDoc(MultipartFile file) throws Exception {
        try {
            // Apache POI HWPF for legacy .doc
            Class<?> docClass = Class.forName("org.apache.poi.hwpf.HWPFDocument");
            Object doc = docClass.getConstructor(java.io.InputStream.class)
                    .newInstance(file.getInputStream());

            Class<?> extractorClass = Class.forName("org.apache.poi.hwpf.extractor.WordExtractor");
            Object extractor = extractorClass.getConstructor(docClass).newInstance(doc);
            String text = (String) extractorClass.getMethod("getText").invoke(extractor);

            extractor.getClass().getMethod("close").invoke(extractor);
            return text;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Apache POI HWPF is not on the classpath. Add org.apache.poi:poi-scratchpad:5.x to pom.xml", e);
        }
    }

    // ── LLM prompt for extraction ─────────────────────────────────────────────

    private String buildExtractionPrompt(String rawText) {
        return """
You are a resume parser. Extract all information from the resume text below and return it
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
      "description": "bullet points joined with newlines"
    }
  ],
  "education": [
    {
      "institution": "university or school name",
      "degree": "degree type e.g. Bachelor of Science",
      "field": "field of study",
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
- Extract every work experience entry found, oldest to newest
- skills[] should be a flat list of individual skill names (no categories)
- Dates must be YYYY-MM format; if only year is found use YYYY-01
- If a field is not present in the resume, return blank string or empty array
- Return raw JSON only
""";
    }

    private String sanitize(String response) {
        if (response == null) return "{}";
        return response.replace("```json", "").replace("```", "").trim();
    }
}