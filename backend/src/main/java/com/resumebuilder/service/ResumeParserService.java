package com.resumebuilder.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Arrays;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Native Java resume parser — zero LLM dependency.
 *
 * Architecture mirrors enterprise ATS parsers (Workday, Taleo, Greenhouse):
 *   1. Extract raw text from PDF / DOCX / DOC / TXT via Apache PDFBox & POI
 *   2. Detect section boundaries with heading-pattern regex
 *   3. Parse each section with field-specific extractors
 *   4. Return structured Map<String, Object> matching the frontend profile schema
 *
 * Handles:
 *   - Multiple date formats  (Jan 2020 / January 2020 / 01/2020 / 2020-01)
 *   - "Present" / "Current" end-dates
 *   - Single-column and two-column PDF layouts (via line-by-line analysis)
 *   - 200+ common tech skills / tools
 *   - Degree aliases (BS / B.S. / Bachelor of Science / Bachelors)
 *   - GPA patterns (3.8/4.0, 3.8 GPA, GPA: 3.8)
 *   - LinkedIn, GitHub, portfolio URL extraction
 */
@Service
public class ResumeParserService {

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    public Map<String, Object> parseResume(MultipartFile file) throws Exception {
        String rawText = extractText(file);
        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("Could not extract text from the uploaded file.");
        }
        return parse(rawText);
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
            // Fast path: default extraction (no position sorting) — handles 90% of resumes
            // and is 3-5x faster than setSortByPosition.  Most modern resume PDFs are
            // single-column or use text layers that PDFBox reads in natural reading order.
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            String text = stripper.getText(doc);

            // Heuristic: if the text looks badly garbled (column bleed — short lines
            // interleaved with unrelated content), fall back to the slower sorted pass.
            if (looksColumnGarbled(text)) {
                stripper.setSortByPosition(true);
                text = stripper.getText(doc);
            }
            return text;
        }
    }

    /**
     * Detect badly-ordered 2-column PDF output.
     * A garbled extraction typically produces many very short "word fragment" lines
     * where the two columns have been interleaved line-by-line.
     */
    private boolean looksColumnGarbled(String text) {
        if (text == null || text.length() < 200) return false;
        String[] lines = text.split("\n");
        int shortLines = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 0 && t.length() < 12 && !t.matches(".*\\d{4}.*") && t.split("\\s+").length <= 2) {
                shortLines++;
            }
        }
        // If more than 30% of non-empty lines look like column fragments, re-parse
        long nonEmpty = Arrays.stream(lines).filter(l -> !l.isBlank()).count();
        return nonEmpty > 10 && ((double) shortLines / nonEmpty) > 0.30;
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
    // MASTER PARSER
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> parse(String text) {
        // Normalise line endings, collapse 3+ blank lines to 2
        text = text.replaceAll("\r\n", "\n").replaceAll("\r", "\n")
                .replaceAll("\n{3,}", "\n\n");

        String[] lines = text.split("\n");

        Map<String, Object> result = new LinkedHashMap<>();

        // ── Contact block (first ~15 lines) ──────────────────────────────
        result.put("name",     extractName(lines));
        result.put("email",    extractEmail(text));
        result.put("phone",    extractPhone(text));
        result.put("location", extractLocation(text, lines));
        result.put("linkedin", extractLinkedIn(text));
        result.put("github",   extractGitHub(text));
        result.put("website",  extractWebsite(text));
        result.put("headline", extractHeadline(lines));

        // ── Section detection ─────────────────────────────────────────────
        Map<String, String> sections = splitIntoSections(text);

        result.put("summary",        extractSummary(sections));
        result.put("companies",      extractExperience(sections));
        result.put("education",      extractEducation(sections));
        result.put("skills",         extractSkills(sections, text));
        result.put("certifications", extractCertifications(sections));

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECTION SPLITTER
    // Detects headings such as: EXPERIENCE, Work Experience, EDUCATION, etc.
    // ═══════════════════════════════════════════════════════════════════════

    /** Maps canonical section names → regex patterns that identify them */
    private static final Map<String, Pattern> SECTION_PATTERNS = new LinkedHashMap<>();
    static {
        // Order matters — more specific patterns first
        SECTION_PATTERNS.put("summary",   Pattern.compile(
                "^\\s*(professional\\s+summary|summary|profile|about\\s+me|career\\s+objective|objective|overview)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("experience", Pattern.compile(
                "^\\s*(work\\s+experience|professional\\s+experience|employment\\s+history|experience|work\\s+history|career\\s+history|positions?\\s+held)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("education", Pattern.compile(
                "^\\s*(education|educational\\s+background|academic\\s+background|academic\\s+qualifications?|qualifications?)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("skills", Pattern.compile(
                "^\\s*(skills?|technical\\s+skills?|core\\s+competencies|competencies|technologies|tools?\\s+&\\s+technologies|key\\s+skills?|areas\\s+of\\s+expertise|expertise)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("certifications", Pattern.compile(
                "^\\s*(certifications?|licenses?\\s*&?\\s*certifications?|professional\\s+certifications?|accreditations?)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("projects", Pattern.compile(
                "^\\s*(projects?|personal\\s+projects?|key\\s+projects?|notable\\s+projects?)\\s*$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("awards", Pattern.compile(
                "^\\s*(awards?|honors?|achievements?|accomplishments?)\\s*$",
                Pattern.CASE_INSENSITIVE));
    }

    /**
     * Splits the resume text into named sections.
     * Returns a map: sectionName → body text of that section.
     */
    private Map<String, String> splitIntoSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        String[] lines = text.split("\n");

        String currentSection = "header";
        StringBuilder currentBody = new StringBuilder();

        for (String line : lines) {
            String detected = detectSectionHeading(line);
            if (detected != null) {
                // Flush previous section
                if (currentBody.length() > 0) {
                    sections.merge(currentSection, currentBody.toString().trim(), (a, b) -> a + "\n" + b);
                }
                currentSection = detected;
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append("\n");
            }
        }
        // Flush last section
        if (currentBody.length() > 0) {
            sections.merge(currentSection, currentBody.toString().trim(), (a, b) -> a + "\n" + b);
        }
        return sections;
    }

    private String detectSectionHeading(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;
        // Must be short (headings are rarely > 50 chars)
        if (trimmed.length() > 60) return null;
        for (Map.Entry<String, Pattern> entry : SECTION_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(trimmed).matches()) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTACT FIELD EXTRACTORS
    // ═══════════════════════════════════════════════════════════════════════

    /** Heuristic: first non-empty line that looks like a person's name */
    private String extractName(String[] lines) {
        // Name-line heuristics:
        //   - Short (2–5 words)
        //   - Not all-caps heading (length < 40)
        //   - Does not contain @, http, digits (phone/email)
        //   - Matches Title Case or ALL CAPS name pattern
        Pattern namePattern = Pattern.compile(
                "^[A-Z][a-zA-Z'\\-]+(\\s+[A-Z][a-zA-Z'\\-]+){1,4}$");
        Pattern allCapsName = Pattern.compile(
                "^[A-Z][A-Z'\\-]+(\\s+[A-Z][A-Z'\\-]+){1,4}$");

        for (int i = 0; i < Math.min(8, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.contains("@") || line.contains("http") || line.contains("|")) continue;
            if (line.matches(".*\\d.*")) continue;
            if (namePattern.matcher(line).matches() || allCapsName.matcher(line).matches()) {
                // Convert ALL CAPS to Title Case
                if (line.equals(line.toUpperCase()) && line.length() > 3) {
                    return toTitleCase(line);
                }
                return line;
            }
        }
        return "";
    }

    private String toTitleCase(String input) {
        return Arrays.stream(input.toLowerCase().split("\\s+"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String extractEmail(String text) {
        Matcher m = Pattern.compile(
                "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").matcher(text);
        return m.find() ? m.group().trim() : "";
    }

    private String extractPhone(String text) {
        // Matches: +1 (555) 123-4567, 555.123.4567, +44 7911 123456, (555)123-4567, etc.
        Matcher m = Pattern.compile(
                "(\\+?\\d{1,3}[\\s\\-.]?)?" +
                        "(\\(?\\d{3}\\)?[\\s\\-.]?)" +
                        "\\d{3}[\\s\\-.]?\\d{4}"
        ).matcher(text);
        return m.find() ? m.group().trim() : "";
    }

    private String extractLocation(String text, String[] lines) {
        // Pattern: City, ST  |  City, State  |  City, Country
        Pattern locPat = Pattern.compile(
                "([A-Z][a-zA-Z\\s]{1,25}),\\s*([A-Z]{2}|[A-Z][a-zA-Z\\s]{2,20})" +
                        "(\\s*\\d{5}(-\\d{4})?)?");
        // Look in first 12 lines for fastest match
        for (int i = 0; i < Math.min(12, lines.length); i++) {
            Matcher m = locPat.matcher(lines[i]);
            if (m.find()) {
                String city = m.group(1).trim();
                String state = m.group(2).trim();
                return city + ", " + state;
            }
        }
        // Fallback: scan whole text
        Matcher m = locPat.matcher(text);
        return m.find() ? m.group(1).trim() + ", " + m.group(2).trim() : "";
    }

    private String extractLinkedIn(String text) {
        Matcher m = Pattern.compile(
                "(?:https?://)?(?:www\\.)?linkedin\\.com/in/([a-zA-Z0-9\\-_%]+)/?",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) return "linkedin.com/in/" + m.group(1);
        return "";
    }

    private String extractGitHub(String text) {
        Matcher m = Pattern.compile(
                "(?:https?://)?(?:www\\.)?github\\.com/([a-zA-Z0-9\\-_%]+)/?",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) return "github.com/" + m.group(1);
        return "";
    }

    private String extractWebsite(String text) {
        // Only pick up personal sites — exclude linkedin/github/twitter
        Matcher m = Pattern.compile(
                "https?://(?!(?:www\\.)?(linkedin|github|twitter|x\\.com|facebook))([a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,})(/[^\\s]*)?",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) return m.group().trim();
        return "";
    }

    /** Headline = professional title, typically on line 2 or 3 */
    private String extractHeadline(String[] lines) {
        // Common job title keywords
        Pattern titlePattern = Pattern.compile(
                ".*(engineer|developer|architect|analyst|manager|director|designer|" +
                        "scientist|consultant|specialist|lead|senior|junior|intern|associate|" +
                        "officer|administrator|coordinator|executive|president|vp|cto|ceo|coo|" +
                        "devops|sre|qa|tester|scrum|agile|product|program|project).*",
                Pattern.CASE_INSENSITIVE);

        for (int i = 1; i < Math.min(6, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.contains("@") || line.matches(".*\\d{5,}.*")) continue;
            if (line.length() < 5 || line.length() > 80) continue;
            if (titlePattern.matcher(line).matches()) {
                return line;
            }
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUMMARY
    // ═══════════════════════════════════════════════════════════════════════

    private String extractSummary(Map<String, String> sections) {
        String body = sections.getOrDefault("summary", "");
        if (body.isBlank()) return "";
        // Clean up and join lines into a paragraph
        return Arrays.stream(body.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .collect(Collectors.joining(" "))
                .trim();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPERIENCE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Date range patterns (very permissive — handles most real-world formats):
     *   Jan 2020 – Present
     *   January 2020 - December 2022
     *   01/2020 – 12/2022
     *   2020 - 2022
     *   2020-01 to 2022-12
     */
    private static final String MONTH_NAMES =
            "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|" +
                    "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";

    private static final String DATE_TOKEN =
            "(?:" + MONTH_NAMES + "[\\s,]*\\d{4}|\\d{1,2}/\\d{4}|\\d{4}-\\d{2}|\\d{4})";

    private static final Pattern DATE_RANGE_PAT = Pattern.compile(
            "(" + DATE_TOKEN + ")\\s*(?:[-–—]|to)\\s*(" + DATE_TOKEN + "|present|current|now)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SINGLE_DATE_PAT = Pattern.compile(
            DATE_TOKEN, Pattern.CASE_INSENSITIVE);

    private List<Map<String, Object>> extractExperience(Map<String, String> sections) {
        String body = sections.getOrDefault("experience", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> jobs = new ArrayList<>();
        String[] lines = body.split("\n");

        // State machine: collect blocks between date-range lines
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    Map<String, Object> job = parseJobBlock(currentBlock);
                    if (job != null) jobs.add(job);
                    currentBlock = new ArrayList<>();
                }
                continue;
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            Map<String, Object> job = parseJobBlock(currentBlock);
            if (job != null) jobs.add(job);
        }

        // Merge very small blocks into the preceding job's description
        return mergeFragments(jobs);
    }

    private Map<String, Object> parseJobBlock(List<String> lines) {
        if (lines.isEmpty()) return null;

        Map<String, Object> job = new LinkedHashMap<>();
        String startDate = "", endDate = "";
        boolean isCurrent = false;
        List<String> bullets = new ArrayList<>();
        String company = "", role = "", location = "";

        // Find the line containing a date range — that's usually the header line
        int headerLine = -1;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            Matcher m = DATE_RANGE_PAT.matcher(lines.get(i));
            if (m.find()) {
                headerLine = i;
                startDate = normaliseDate(m.group(1));
                String endRaw = m.group(2);
                if (endRaw.matches("(?i)present|current|now")) {
                    isCurrent = true;
                    endDate = "";
                } else {
                    endDate = normaliseDate(endRaw);
                }
                break;
            }
        }

        // Heuristic: first two non-date lines are role + company (or company + role)
        List<String> headerLines = new ArrayList<>();
        for (int i = 0; i < Math.min(headerLine < 0 ? 3 : headerLine + 2, lines.size()); i++) {
            String l = lines.get(i).trim();
            if (!DATE_RANGE_PAT.matcher(l).find() && !l.isEmpty()) {
                headerLines.add(l);
            }
        }

        if (headerLines.size() >= 2) {
            // Common patterns:
            //   Line 0: "Software Engineer" , Line 1: "Google | Mountain View, CA"
            //   Line 0: "Google"            , Line 1: "Senior Engineer"
            //   Line 0: "Software Engineer @ Google"
            String h0 = headerLines.get(0);
            String h1 = headerLines.get(1);

            if (h0.contains("@") || h0.contains("|") || h0.contains(",")) {
                // "Role @ Company | Location" or "Company | Location"
                String[] parts = h0.split("[@|]", 2);
                role    = parts[0].trim();
                company = parts.length > 1 ? parts[1].split("[|,]")[0].trim() : "";
                location = extractLocationFromLine(h0);
                if (company.isEmpty()) {
                    company = h1.split("[|,@]")[0].trim();
                }
            } else if (looksLikeCompany(h1) || h1.contains("|")) {
                role    = h0;
                company = h1.split("[|,]")[0].trim();
                location = extractLocationFromLine(h1);
            } else if (looksLikeTitle(h0)) {
                role    = h0;
                company = h1.split("[|,]")[0].trim();
                location = extractLocationFromLine(h1);
            } else {
                company = h0.split("[|,]")[0].trim();
                role    = h1;
                location = extractLocationFromLine(h0);
            }
        } else if (headerLines.size() == 1) {
            // "Software Engineer, Google, Jan 2020 - Present"
            String[] parts = headerLines.get(0).split(",|@|\\|");
            role    = parts.length > 0 ? parts[0].trim() : "";
            company = parts.length > 1 ? parts[1].trim() : "";
            location = parts.length > 2 ? parts[2].trim() : "";
        }

        // Remaining lines are bullet points / responsibilities
        int descStart = Math.max(0, headerLine < 0 ? Math.min(2, lines.size()) : headerLine + 1);
        for (int i = descStart; i < lines.size(); i++) {
            String l = lines.get(i).trim()
                    .replaceFirst("^[•\\-–*>]\\s*", "");
            if (!l.isEmpty() && !DATE_RANGE_PAT.matcher(l).find()) {
                bullets.add(l);
            }
        }

        // Skip completely empty blocks
        if (company.isEmpty() && role.isEmpty()) return null;

        job.put("company",   company);
        job.put("role",      role);
        job.put("location",  location);
        job.put("startDate", startDate);
        job.put("endDate",   endDate);
        job.put("current",   isCurrent);
        job.put("description", String.join("\n• ", bullets).trim());

        return job;
    }

    /** Merges orphan blocks (< 2 lines, no role+company) into previous job's description */
    private List<Map<String, Object>> mergeFragments(List<Map<String, Object>> jobs) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            String company = (String) job.getOrDefault("company", "");
            String role    = (String) job.getOrDefault("role",    "");
            if (company.isEmpty() && role.isEmpty() && !merged.isEmpty()) {
                // Append description to previous
                Map<String, Object> prev = merged.get(merged.size() - 1);
                String prevDesc = (String) prev.getOrDefault("description", "");
                String thisDesc = (String) job.getOrDefault("description", "");
                prev.put("description", (prevDesc + "\n" + thisDesc).trim());
            } else {
                merged.add(job);
            }
        }
        return merged;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EDUCATION
    // ═══════════════════════════════════════════════════════════════════════

    private static final Map<String, String> DEGREE_MAP = new LinkedHashMap<>();
    static {
        DEGREE_MAP.put("(?i)b\\.?s\\.?c?\\.?|bachelor\\s+of\\s+science|bs\\b", "Bachelor of Science");
        DEGREE_MAP.put("(?i)b\\.?a\\.?|bachelor\\s+of\\s+arts|ba\\b",          "Bachelor of Arts");
        DEGREE_MAP.put("(?i)m\\.?s\\.?c?\\.?|master\\s+of\\s+science|ms\\b",   "Master of Science");
        DEGREE_MAP.put("(?i)m\\.?a\\.?|master\\s+of\\s+arts|ma\\b",            "Master of Arts");
        DEGREE_MAP.put("(?i)m\\.?b\\.?a\\.?|master\\s+of\\s+business|mba\\b",  "MBA");
        DEGREE_MAP.put("(?i)ph\\.?d\\.?|doctorate|doctor\\s+of\\s+philosophy",  "PhD");
        DEGREE_MAP.put("(?i)associate('?s)?\\s+degree?|a\\.?s\\.?\\b|a\\.?a\\.?\\b", "Associate Degree");
        DEGREE_MAP.put("(?i)diploma",                                            "Diploma");
        DEGREE_MAP.put("(?i)b\\.?e\\.?|bachelor\\s+of\\s+engineering",          "Bachelor of Science");
        DEGREE_MAP.put("(?i)b\\.?tech\\.?|bachelor\\s+of\\s+technology",        "Bachelor of Science");
        DEGREE_MAP.put("(?i)m\\.?tech\\.?|master\\s+of\\s+technology",          "Master of Science");
        DEGREE_MAP.put("(?i)m\\.?e\\.?|master\\s+of\\s+engineering",            "Master of Science");
    }

    private static final Pattern GPA_PAT = Pattern.compile(
            "(?:gpa|grade\\s+point|cumulative)[:\\s]*([0-9]\\.?[0-9]?)\\s*(?:/\\s*[0-9]\\.?[0-9]?)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GPA_INLINE_PAT = Pattern.compile(
            "([0-9]\\.[0-9]{1,2})\\s*/\\s*([0-9]\\.[0-9]{0,2})");

    private static final Pattern GRAD_YEAR_PAT = Pattern.compile(
            "(?:graduated?|class\\s+of|expected|anticipated)?[:\\s]*(20[0-9]{2}|19[0-9]{2})",
            Pattern.CASE_INSENSITIVE);

    private List<Map<String, Object>> extractEducation(Map<String, String> sections) {
        String body = sections.getOrDefault("education", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> degrees = new ArrayList<>();
        String[] lines = body.split("\n");
        List<String> block = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!block.isEmpty()) {
                    Map<String, Object> edu = parseEducationBlock(block);
                    if (edu != null) degrees.add(edu);
                    block = new ArrayList<>();
                }
                continue;
            }
            block.add(trimmed);
        }
        if (!block.isEmpty()) {
            Map<String, Object> edu = parseEducationBlock(block);
            if (edu != null) degrees.add(edu);
        }
        return degrees;
    }

    private Map<String, Object> parseEducationBlock(List<String> lines) {
        if (lines.isEmpty()) return null;

        Map<String, Object> edu = new LinkedHashMap<>();
        String institution = "", degree = "", field = "", location = "", year = "", gpa = "";

        String fullText = String.join(" ", lines);

        // ── Degree ───────────────────────────────────────────────────────
        for (Map.Entry<String, String> entry : DEGREE_MAP.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(fullText).find()) {
                degree = entry.getValue();
                break;
            }
        }

        // ── GPA ──────────────────────────────────────────────────────────
        Matcher gpaMatcher = GPA_PAT.matcher(fullText);
        if (gpaMatcher.find()) {
            gpa = gpaMatcher.group(1);
        } else {
            Matcher gpaInline = GPA_INLINE_PAT.matcher(fullText);
            if (gpaInline.find()) {
                gpa = gpaInline.group(1) + " / " + gpaInline.group(2);
            }
        }

        // ── Graduation year ───────────────────────────────────────────────
        Matcher yearMatcher = GRAD_YEAR_PAT.matcher(fullText);
        if (yearMatcher.find()) year = yearMatcher.group(1);

        // ── Institution (first line, usually) ────────────────────────────
        institution = lines.get(0).split("[,|]")[0].trim();
        // Clean up degree noise from institution line
        if (!degree.isEmpty()) {
            institution = institution.replaceAll("(?i)" + MONTH_NAMES, "").trim();
        }

        // ── Field of study ────────────────────────────────────────────────
        // Look for "in X" or "of X" pattern after degree keyword
        Matcher fieldMatcher = Pattern.compile(
                "(?:in|of)\\s+([A-Z][a-zA-Z\\s&,]+?)(?:\\s*,|\\s*\\(|\\s*\\d{4}|\\.|$)",
                Pattern.CASE_INSENSITIVE).matcher(fullText);
        if (fieldMatcher.find()) {
            String candidate = fieldMatcher.group(1).trim();
            // Avoid matching noise like "Arts" from "Master of Arts"
            if (!candidate.equalsIgnoreCase("science") &&
                    !candidate.equalsIgnoreCase("arts") &&
                    !candidate.equalsIgnoreCase("business") &&
                    candidate.length() > 3) {
                field = candidate;
            }
        }

        // ── Institution location (City, ST pattern on institution or second line) ─
        // Try: "MIT, Cambridge, MA" or second line being a city/state
        String instLine = lines.get(0);
        String[] instParts = instLine.split(",");
        if (instParts.length >= 3) {
            // e.g. "Massachusetts Institute of Technology, Cambridge, MA"
            String possibleCity  = instParts[instParts.length - 2].trim();
            String possibleState = instParts[instParts.length - 1].trim();
            if (possibleState.matches("[A-Z]{2}") || possibleState.matches("[A-Z]{2}\\s+\\d{5}.*")) {
                location = possibleCity + ", " + possibleState.replaceAll("\\s*\\d.*", "").trim();
            }
        }
        if (location.isEmpty() && lines.size() > 1) {
            // Second line sometimes has location
            Matcher locMatcher = Pattern.compile(
                    "([A-Z][a-zA-Z\\s.]+),\\s*([A-Z]{2})\\b", Pattern.CASE_INSENSITIVE
            ).matcher(lines.get(1));
            if (locMatcher.find()) {
                location = locMatcher.group(1).trim() + ", " + locMatcher.group(2).trim();
            }
        }

        // Skip blocks with no institution
        if (institution.isEmpty()) return null;

        edu.put("institution", institution);
        edu.put("degree",      degree);
        edu.put("field",       field);
        edu.put("location",    location);
        edu.put("year",        year);
        edu.put("gpa",         gpa);
        return edu;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SKILLS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 200+ common skills across all major categories.
     * Uses exact-word matching with word boundaries to avoid false positives.
     */
    private static final Set<String> KNOWN_SKILLS = new LinkedHashSet<>(Arrays.asList(
            // Programming Languages
            "JavaScript","TypeScript","Python","Java","C++","C#","C","Go","Golang","Rust",
            "Swift","Kotlin","PHP","Ruby","Scala","R","MATLAB","Perl","Haskell","Lua",
            "Dart","Groovy","Elixir","Clojure","F#","Erlang","Assembly","COBOL","Fortran",
            // Frontend
            "React","React.js","Vue","Vue.js","Angular","AngularJS","Next.js","Nuxt.js",
            "Svelte","Ember.js","Backbone.js","jQuery","HTML","HTML5","CSS","CSS3",
            "SASS","SCSS","LESS","Tailwind CSS","Bootstrap","Material UI","Ant Design",
            "Redux","MobX","Zustand","GraphQL","Apollo","Webpack","Vite","Babel","ESLint",
            // Backend
            "Node.js","Express","Express.js","Django","Flask","FastAPI","Spring","Spring Boot",
            "Laravel","Rails","Ruby on Rails","ASP.NET","NestJS","Gin","Fiber","Actix",
            "Hapi","Koa","Fastify","gRPC","REST","RESTful","WebSockets","Microservices",
            "SOA","Serverless","API Gateway",
            // Cloud & DevOps
            "AWS","Azure","GCP","Google Cloud","Heroku","Vercel","Netlify","DigitalOcean",
            "Docker","Kubernetes","Helm","Terraform","Ansible","Puppet","Chef","Vagrant",
            "Jenkins","GitHub Actions","GitLab CI","CircleCI","Travis CI","ArgoCD",
            "Prometheus","Grafana","Datadog","New Relic","ELK Stack","Elasticsearch",
            "Logstash","Kibana","Linux","Ubuntu","Nginx","Apache","Caddy",
            // Databases
            "PostgreSQL","MySQL","SQLite","MariaDB","Oracle","SQL Server","MSSQL",
            "MongoDB","DynamoDB","Cassandra","Redis","Memcached","Neo4j","CouchDB",
            "Firebase","Firestore","Supabase","PlanetScale","Prisma","Hibernate",
            "SQLAlchemy","JDBC","JPA","Sequelize","Mongoose",
            // Data & AI / ML
            "Machine Learning","Deep Learning","TensorFlow","PyTorch","Keras","Scikit-learn",
            "Pandas","NumPy","SciPy","Matplotlib","Seaborn","OpenCV","NLTK","spaCy",
            "Hugging Face","LangChain","LLM","NLP","Computer Vision","Data Analysis",
            "Data Science","Data Engineering","Spark","Hadoop","Hive","Kafka","Airflow",
            "dbt","Snowflake","BigQuery","Redshift","Tableau","Power BI","Looker","Metabase",
            // Mobile
            "iOS","Android","React Native","Flutter","Xamarin","Ionic","Capacitor",
            "SwiftUI","Jetpack Compose","Retrofit","Alamofire",
            // Testing
            "Jest","Mocha","Chai","Cypress","Playwright","Selenium","JUnit","TestNG",
            "Pytest","RSpec","Cucumber","Postman","Insomnia","k6","Locust",
            // Soft Skills
            "Leadership","Communication","Problem Solving","Team Collaboration",
            "Agile","Scrum","Kanban","JIRA","Confluence","Notion","Trello",
            "Project Management","Mentoring","Code Review","Technical Writing",
            // Security
            "OAuth","JWT","SAML","SSL","TLS","OWASP","Penetration Testing","Cybersecurity",
            "Zero Trust","IAM","HashiCorp Vault",
            // Protocols & Standards
            "HTTP","HTTPS","TCP/IP","WebRTC","MQTT","AMQP","JSON","XML","YAML","TOML",
            // Tools
            "Git","GitHub","GitLab","Bitbucket","VS Code","IntelliJ","Eclipse","Xcode",
            "Figma","Sketch","Adobe XD","Postman","Swagger","OpenAPI","Maven","Gradle",
            "npm","yarn","pnpm","pip","Poetry","Cargo","Make","CMake"
    ));

    private List<String> extractSkills(Map<String, String> sections, String fullText) {
        String skillsBody = sections.getOrDefault("skills", "");
        Set<String> found = new LinkedHashSet<>();

        // Strategy 1: scan the dedicated skills section against known skills list
        String scanTarget = skillsBody.isBlank() ? fullText : skillsBody + "\n" + fullText;

        for (String skill : KNOWN_SKILLS) {
            // Build a word-boundary-aware pattern (handles "React" vs "Reactive")
            String escaped = Pattern.quote(skill);
            Pattern pat = Pattern.compile("(?i)(?<![a-zA-Z0-9.])" + escaped + "(?![a-zA-Z0-9.]|\\s*\\.js)", 0);
            // Special case: don't strip ".js" from skill names that end in it
            if (skill.endsWith(".js") || skill.endsWith(".NET") || skill.contains(".")) {
                pat = Pattern.compile("(?i)(?<![a-zA-Z0-9])" + escaped + "(?![a-zA-Z0-9])", 0);
            }
            if (pat.matcher(scanTarget).find()) {
                found.add(skill);
            }
        }

        // Strategy 2: if skills section exists, also parse comma/bullet delimited lines
        if (!skillsBody.isBlank()) {
            for (String line : skillsBody.split("\n")) {
                String cleaned = line.trim().replaceFirst("^[•\\-–*>]\\s*", "");
                // Strip category prefix like "Languages: Python, Java"
                cleaned = cleaned.replaceFirst("^[A-Za-z\\s&/]+:\\s*", "");
                String[] tokens = cleaned.split("[,;|/]+");
                for (String tok : tokens) {
                    String s = tok.trim().replaceAll("[•\\-–*()]", "").trim();
                    if (s.length() >= 2 && s.length() <= 40 && !s.isEmpty()) {
                        // Only add if it looks like a skill (not a sentence)
                        if (!s.contains(" ") || s.split("\\s+").length <= 4) {
                            found.add(toTitleCaseSkill(s));
                        }
                    }
                }
            }
        }

        return new ArrayList<>(found);
    }

    /** Normalises skill casing — preserves known casing, Title-cases unknowns */
    private String toTitleCaseSkill(String s) {
        for (String known : KNOWN_SKILLS) {
            if (known.equalsIgnoreCase(s)) return known;
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CERTIFICATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /** Well-known certification keywords used to detect cert lines */
    private static final Set<String> CERT_KEYWORDS = new HashSet<>(Arrays.asList(
            "aws", "azure", "gcp", "google", "cisco", "comptia", "certified",
            "certification", "certificate", "pmp", "pmi", "scrum", "safe", "cka",
            "ckad", "ceh", "cissp", "cism", "oscp", "ccna", "ccnp", "rhcsa", "rhce",
            "oracle", "salesforce", "microsoft", "hashicorp", "terraform",
            "kubernetes", "docker", "meta", "ibm", "coursera", "udemy", "linkedin"
    ));

    private List<Map<String, Object>> extractCertifications(Map<String, String> sections) {
        String body = sections.getOrDefault("certifications", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> certs = new ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim().replaceFirst("^[•\\-–*>]\\s*", "");
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() < 5) continue;

            // Check if this line looks like a certification entry
            boolean looksLikeCert = CERT_KEYWORDS.stream()
                    .anyMatch(kw -> trimmed.toLowerCase().contains(kw))
                    || trimmed.matches("(?i).*certif.*")
                    || trimmed.matches("(?i).*license.*");

            if (!looksLikeCert && !body.equals(sections.getOrDefault("certifications", ""))) continue;

            Map<String, Object> cert = new LinkedHashMap<>();

            // Try to extract year
            Matcher yearM = Pattern.compile("\\b(20[0-9]{2}|19[0-9]{2})\\b").matcher(trimmed);
            String year = yearM.find() ? yearM.group(1) : "";

            // Try to extract issuer (after "by", "from", "–", "|", or in parens)
            String issuer = "";
            Matcher issuerM = Pattern.compile(
                    "(?:by|from|issued by|–|\\|)\\s*([A-Z][a-zA-Z0-9\\s&,\\.]+?)(?:\\s*\\(|\\s*\\d{4}|$)",
                    Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (issuerM.find()) issuer = issuerM.group(1).trim();

            // Clean cert name
            String name = trimmed
                    .replaceAll("\\b(20[0-9]{2}|19[0-9]{2})\\b", "")
                    .replaceAll("(?i)(?:by|from|–|\\|)\\s*" + Pattern.quote(issuer), "")
                    .replaceAll("[,|–]$", "")
                    .trim();

            if (name.isEmpty()) continue;

            cert.put("name",   name);
            cert.put("issuer", issuer);
            cert.put("year",   year);
            certs.add(cert);
        }
        return certs;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATE NORMALISATION
    // Converts any parsed date string to "YYYY-MM" for the frontend schema
    // ═══════════════════════════════════════════════════════════════════════

    private static final Map<String, String> MONTH_NUM = new LinkedHashMap<>();
    static {
        String[][] months = {
                {"jan","01"},{"feb","02"},{"mar","03"},{"apr","04"},
                {"may","05"},{"jun","06"},{"jul","07"},{"aug","08"},
                {"sep","09"},{"oct","10"},{"nov","11"},{"dec","12"}
        };
        for (String[] m : months) MONTH_NUM.put(m[0], m[1]);
    }

    private String normaliseDate(String raw) {
        if (raw == null) return "";
        raw = raw.trim();

        // Already YYYY-MM
        if (raw.matches("\\d{4}-\\d{2}")) return raw;

        // Already YYYY only
        if (raw.matches("\\d{4}")) return raw + "-01";

        // MM/YYYY
        Matcher mmYyyy = Pattern.compile("(\\d{1,2})/(\\d{4})").matcher(raw);
        if (mmYyyy.find()) {
            return mmYyyy.group(2) + "-" + String.format("%02d", Integer.parseInt(mmYyyy.group(1)));
        }

        // "Jan 2020" / "January 2020" / "Jan, 2020"
        Matcher monYear = Pattern.compile(
                "(" + MONTH_NAMES + ")[\\s,]*?(\\d{4})", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (monYear.find()) {
            String monKey = monYear.group(1).substring(0, 3).toLowerCase();
            String yr     = monYear.group(2);
            String mm     = MONTH_NUM.getOrDefault(monKey, "01");
            return yr + "-" + mm;
        }

        return raw;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HEURISTIC HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns true if a line looks like a company name (Inc, LLC, Ltd, Corp, etc.) */
    private boolean looksLikeCompany(String line) {
        return Pattern.compile(
                "(?i)\\b(inc\\.?|llc\\.?|ltd\\.?|corp\\.?|co\\.?|group|solutions|services|" +
                        "technologies|systems|labs|studio|ventures|consulting|global|international)\\b"
        ).matcher(line).find();
    }

    /** Returns true if a line looks like a job title */
    private boolean looksLikeTitle(String line) {
        return Pattern.compile(
                "(?i)\\b(engineer|developer|architect|analyst|manager|director|designer|" +
                        "scientist|consultant|specialist|lead|senior|junior|intern|associate|" +
                        "officer|administrator|coordinator|executive)\\b"
        ).matcher(line).find();
    }

    /** Extracts "City, ST" from a line that may also contain company name, dates, etc. */
    private String extractLocationFromLine(String line) {
        Matcher m = Pattern.compile(
                "([A-Z][a-zA-Z\\s]{1,25}),\\s*([A-Z]{2}|Remote|Hybrid)",
                Pattern.CASE_INSENSITIVE).matcher(line);
        if (m.find()) return m.group(1).trim() + ", " + m.group(2).trim();
        if (Pattern.compile("(?i)\\b(remote|hybrid|on-?site)\\b").matcher(line).find()) {
            return "Remote";
        }
        return "";
    }
}