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
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Native Java resume parser — zero LLM dependency.
 *
 * Design goals:
 *  1. Fast  — default PDF extraction (no position sort), fallback only if garbled
 *  2. Accurate — multi-pass contact extraction, robust section splitting with ALL-CAPS
 *               heading support, heuristic role/company disambiguation
 *  3. Correct schema — all keys match the frontend profile model exactly
 *
 * v2 improvements:
 *  - extractName: aggressively rejects pipe-separated contact blocks and multi-separator lines
 *  - extractSkills: true word-boundary matching; short/special-char skills use strict fencing
 *  - assignRoleAndCompany: refactored to plain array return; strips work-arrangement suffixes
 *  - parseEducationBlock: more robust location extraction from comma-delimited institution lines
 *  - normaliseDate: handles Q1/Q2/Q3/Q4, seasons, YYYY-MM with em-dash, and more edge cases
 *  - detectSectionHeading: rejects lines beginning with bullet characters (false headings)
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
    // TEXT EXTRACTION
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
            stripper.setSortByPosition(false);
            String text = stripper.getText(doc);
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
            if (t.length() < 15 && !t.matches(".*\\d{4}.*") && t.split("\\s+").length <= 2) shortCount++;
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
    // MASTER PARSER
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> parse(String rawText) {
        String text = rawText
                .replaceAll("\r\n|\r", "\n")
                .replaceAll("[ \t]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        String[] lines = text.split("\n");
        Map<String, String> sections = splitIntoSections(text);

        String name     = extractName(lines, sections);
        String email    = extractEmail(text);
        String phone    = extractPhone(text);
        String location = extractLocation(lines, text);
        String linkedin = extractLinkedIn(text);
        String github   = extractGitHub(text);
        String website  = extractWebsite(text);
        String headline = extractHeadline(lines, name);

        String            summary  = extractSummary(sections, lines, name, headline);
        List<Map<String,Object>> companies = extractExperience(sections);
        List<Map<String,Object>> education = extractEducation(sections);
        List<String>      skills   = extractSkills(sections, text);
        List<Map<String,Object>> certs = extractCertifications(sections);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name",           name);
        result.put("email",          email);
        result.put("phone",          phone);
        result.put("location",       location);
        result.put("linkedin",       linkedin);
        result.put("github",         github);
        result.put("website",        website);
        result.put("headline",       headline);
        result.put("summary",        summary);
        result.put("companies",      companies);
        result.put("education",      education);
        result.put("skills",         skills);
        result.put("certifications", certs);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECTION SPLITTER
    // ═══════════════════════════════════════════════════════════════════════

    private static final LinkedHashMap<String, Pattern> SECTION_PATTERNS = new LinkedHashMap<>();
    static {
        SECTION_PATTERNS.put("summary", Pattern.compile(
                "^(PROFESSIONAL\\s+SUMMARY|SUMMARY|PROFILE|ABOUT\\s+ME|CAREER\\s+OBJECTIVE|" +
                        "OBJECTIVE|OVERVIEW|Professional\\s+Summary|Summary|Profile|About\\s+Me|" +
                        "Career\\s+Objective|Objective|Overview)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("experience", Pattern.compile(
                "^(WORK\\s+EXPERIENCE|PROFESSIONAL\\s+EXPERIENCE|EMPLOYMENT\\s+HISTORY|" +
                        "EXPERIENCE|WORK\\s+HISTORY|CAREER\\s+HISTORY|RELEVANT\\s+EXPERIENCE|" +
                        "Work\\s+Experience|Professional\\s+Experience|Employment\\s+History|Experience|" +
                        "Work\\s+History|Career\\s+History|Relevant\\s+Experience)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("education", Pattern.compile(
                "^(EDUCATION|EDUCATIONAL\\s+BACKGROUND|ACADEMIC\\s+BACKGROUND|" +
                        "ACADEMIC\\s+QUALIFICATIONS?|QUALIFICATIONS?|" +
                        "Education|Educational\\s+Background|Academic\\s+Background|" +
                        "Academic\\s+Qualifications?|Qualifications?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("skills", Pattern.compile(
                "^(SKILLS?|TECHNICAL\\s+SKILLS?|CORE\\s+COMPETENCIES|COMPETENCIES|" +
                        "TECHNOLOGIES|TOOLS?\\s+[&AND]+\\s+TECHNOLOGIES|KEY\\s+SKILLS?|" +
                        "AREAS\\s+OF\\s+EXPERTISE|EXPERTISE|TECHNICAL\\s+EXPERTISE|" +
                        "Skills?|Technical\\s+Skills?|Core\\s+Competencies|Competencies|" +
                        "Technologies|Tools?\\s+[&And]+\\s+Technologies|Key\\s+Skills?|" +
                        "Areas\\s+of\\s+Expertise|Expertise|Technical\\s+Expertise)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("certifications", Pattern.compile(
                "^(CERTIFICATIONS?|LICENSES?\\s*[&AND]*\\s*CERTIFICATIONS?|" +
                        "PROFESSIONAL\\s+CERTIFICATIONS?|ACCREDITATIONS?|CREDENTIALS?|" +
                        "Certifications?|Licenses?\\s*[&And]*\\s*Certifications?|" +
                        "Professional\\s+Certifications?|Accreditations?|Credentials?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("projects", Pattern.compile(
                "^(PROJECTS?|PERSONAL\\s+PROJECTS?|KEY\\s+PROJECTS?|NOTABLE\\s+PROJECTS?|" +
                        "Projects?|Personal\\s+Projects?|Key\\s+Projects?|Notable\\s+Projects?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("awards", Pattern.compile(
                "^(AWARDS?|HONORS?|ACHIEVEMENTS?|ACCOMPLISHMENTS?|" +
                        "Awards?|Honors?|Achievements?|Accomplishments?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("languages", Pattern.compile(
                "^(LANGUAGES?|SPOKEN\\s+LANGUAGES?|Languages?|Spoken\\s+Languages?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("volunteer", Pattern.compile(
                "^(VOLUNTEER|VOLUNTEERING|COMMUNITY\\s+SERVICE|" +
                        "Volunteer|Volunteering|Community\\s+Service)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("publications", Pattern.compile(
                "^(PUBLICATIONS?|RESEARCH|PAPERS?|Publications?|Research|Papers?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("interests", Pattern.compile(
                "^(INTERESTS?|HOBBIES|ACTIVITIES|Interests?|Hobbies|Activities)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
        SECTION_PATTERNS.put("references", Pattern.compile(
                "^(REFERENCES?|References?)\\s*:?$",
                Pattern.CASE_INSENSITIVE));
    }

    private static final Pattern BULLET_PREFIX = Pattern.compile(
            "^[•●▸▹◦▪\\-–—*>]\\s");

    private Map<String, String> splitIntoSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        String[] lines = text.split("\n");
        String currentSection = "header";
        StringBuilder currentBody = new StringBuilder();

        for (String line : lines) {
            String detected = detectSectionHeading(line);
            if (detected != null) {
                String body = currentBody.toString().trim();
                if (!body.isEmpty()) {
                    sections.merge(currentSection, body, (a, b) -> a + "\n" + b);
                }
                currentSection = detected;
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append("\n");
            }
        }
        String body = currentBody.toString().trim();
        if (!body.isEmpty()) {
            sections.merge(currentSection, body, (a, b) -> a + "\n" + b);
        }
        return sections;
    }

    private String detectSectionHeading(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;
        // Lines starting with bullet chars are never headings
        if (BULLET_PREFIX.matcher(trimmed).find()) return null;
        // Headings don't contain email or URLs
        if (trimmed.contains("@") || trimmed.contains("http")) return null;
        if (trimmed.length() > 70) return null;
        String stripped = trimmed.replaceAll("^[=\\-_*#\\s]+|[=\\-_*#\\s]+$", "").trim();
        if (stripped.isEmpty()) return null;
        for (Map.Entry<String, Pattern> e : SECTION_PATTERNS.entrySet()) {
            if (e.getValue().matcher(stripped).matches()) return e.getKey();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTACT: NAME
    // ═══════════════════════════════════════════════════════════════════════

    private static final Pattern TITLE_KEYWORDS = Pattern.compile(
            "\\b(engineer|developer|architect|analyst|manager|director|designer|scientist|" +
                    "consultant|specialist|lead|senior|junior|intern|associate|officer|administrator|" +
                    "coordinator|executive|president|vp|cto|ceo|coo|ciso|cpo|devops|sre|qa|" +
                    "tester|scrum|agile|product|program|project|full.?stack|frontend|backend|" +
                    "full\\s+stack|cloud|platform|data|software|mobile|security|research)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Multi-strategy name extraction. v2 more aggressively rejects:
     * - Lines with 2+ pipe/bullet separators (contact blocks)
     * - Lines containing phone-pattern digit runs
     * - Lines that look like job titles when the name pattern also matches a title phrase
     */
    private String extractName(String[] lines, Map<String, String> sections) {
        Pattern namePat = Pattern.compile(
                "^([A-Z][a-zA-Z'\\-\\.]{0,20})(\\s+[A-Z][a-zA-Z'\\-\\.]{0,20}){1,3}$");
        Pattern allCapsPat = Pattern.compile(
                "^([A-Z][A-Z'\\-\\.]{0,20})(\\s+[A-Z][A-Z'\\-\\.]{0,20}){1,3}$");

        for (int i = 0; i < Math.min(12, lines.length); i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) continue;

            // Reject lines with multiple separator chars: "Name | Email | Phone"
            long sepCount = raw.chars()
                    .filter(c -> c == '|' || c == '●' || c == '•' || c == '·').count();
            if (sepCount >= 2) continue;

            // Reject lines with phone-like digit patterns
            if (PHONE_PAT.matcher(raw).find()) continue;
            if (raw.matches(".*\\d{7,}.*")) continue;

            // Take first pipe-segment
            String candidate = raw.split("\\s*[|●•·]\\s*")[0].trim();

            if (candidate.contains("@") || candidate.contains("http")
                    || candidate.matches(".*\\d{4,}.*")
                    || candidate.length() < 2 || candidate.length() > 60) continue;

            // Reject clear job title phrases (3+ words containing title keyword, but not name-shaped)
            if (candidate.split("\\s+").length >= 3
                    && TITLE_KEYWORDS.matcher(candidate).find()
                    && !namePat.matcher(candidate).matches()) continue;

            if (namePat.matcher(candidate).matches()) return candidate;
            if (allCapsPat.matcher(candidate).matches() && candidate.length() < 45) {
                return toTitleCase(candidate);
            }
        }
        return "";
    }

    private String toTitleCase(String s) {
        return Arrays.stream(s.toLowerCase().split("\\s+"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTACT: EMAIL / PHONE / LOCATION / LINKEDIN / GITHUB / WEBSITE
    // ═══════════════════════════════════════════════════════════════════════

    private static final Pattern EMAIL_PAT = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,7}");

    private static final Pattern PHONE_PAT = Pattern.compile(
            "(?:(?:\\+?1[\\s.\\-]?)?\\(?([0-9]{3})\\)?[\\s.\\-]?([0-9]{3})[\\s.\\-]?([0-9]{4}))" +
                    "|(?:\\+[1-9][0-9]{1,2}[\\s.\\-]?[0-9]{2,4}[\\s.\\-]?[0-9]{3,4}[\\s.\\-]?[0-9]{3,4})");

    private static final Pattern LOCATION_PAT = Pattern.compile(
            "\\b([A-Z][a-zA-Z\\s]{1,20}),\\s*" +
                    "([A-Z]{2}|Alabama|Alaska|Arizona|Arkansas|California|Colorado|Connecticut|" +
                    "Delaware|Florida|Georgia|Hawaii|Idaho|Illinois|Indiana|Iowa|Kansas|Kentucky|" +
                    "Louisiana|Maine|Maryland|Massachusetts|Michigan|Minnesota|Mississippi|Missouri|" +
                    "Montana|Nebraska|Nevada|New\\s+Hampshire|New\\s+Jersey|New\\s+Mexico|New\\s+York|" +
                    "North\\s+Carolina|North\\s+Dakota|Ohio|Oklahoma|Oregon|Pennsylvania|Rhode\\s+Island|" +
                    "South\\s+Carolina|South\\s+Dakota|Tennessee|Texas|Utah|Vermont|Virginia|Washington|" +
                    "West\\s+Virginia|Wisconsin|Wyoming|" +
                    "UK|Canada|India|Germany|France|Australia|Singapore|Remote)\\b");

    private String extractEmail(String text) {
        Matcher m = EMAIL_PAT.matcher(text);
        return m.find() ? m.group().trim().toLowerCase() : "";
    }

    private String extractPhone(String text) {
        Matcher m = PHONE_PAT.matcher(text);
        return m.find() ? m.group().trim() : "";
    }

    private String extractLocation(String[] lines, String fullText) {
        for (int i = 0; i < Math.min(15, lines.length); i++) {
            String ln = lines[i];
            if (ln.trim().equalsIgnoreCase("remote") || ln.trim().equalsIgnoreCase("hybrid")) {
                return ln.trim();
            }
            Matcher m = LOCATION_PAT.matcher(ln);
            if (m.find()) {
                String city  = m.group(1).trim();
                String state = m.group(2).trim();
                if (city.length() < 3 || KNOWN_SKILLS_LOWER.contains(city.toLowerCase())) continue;
                return city + ", " + state;
            }
        }
        Matcher m = LOCATION_PAT.matcher(fullText);
        if (m.find()) {
            String city  = m.group(1).trim();
            String state = m.group(2).trim();
            return city + ", " + state;
        }
        return "";
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
        Matcher m = Pattern.compile(
                "https?://(?!(?:www\\.)?(linkedin|github|twitter|x\\.com|facebook|instagram))([a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,})(/[^\\s]*)?",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) return m.group().trim();
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTACT: HEADLINE
    // ═══════════════════════════════════════════════════════════════════════

    private String extractHeadline(String[] lines, String name) {
        for (int i = 1; i < Math.min(8, lines.length); i++) {
            String ln = lines[i].trim();
            if (ln.isEmpty()) continue;
            if (ln.equalsIgnoreCase(name)) continue;
            if (ln.contains("@") || ln.contains("linkedin") || ln.contains("github")) continue;
            if (ln.matches(".*\\d{5,}.*")) continue;
            if (ln.length() < 5 || ln.length() > 100) continue;
            if (TITLE_KEYWORDS.matcher(ln).find()) {
                return ln.split("\\s*[|●•·]\\s*")[0].trim();
            }
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUMMARY
    // ═══════════════════════════════════════════════════════════════════════

    private String extractSummary(Map<String, String> sections, String[] allLines,
                                  String name, String headline) {
        String body = sections.getOrDefault("summary", "");
        if (!body.isBlank()) {
            return Arrays.stream(body.split("\n"))
                    .map(String::trim).filter(l -> !l.isEmpty())
                    .collect(Collectors.joining(" ")).trim();
        }
        String headerBody = sections.getOrDefault("header", "");
        if (!headerBody.isBlank()) {
            for (String ln : headerBody.split("\n")) {
                String t = ln.trim();
                if (t.length() > 60 && !t.contains("@") && !PHONE_PAT.matcher(t).find()
                        && !t.equalsIgnoreCase(name) && !t.equalsIgnoreCase(headline)) {
                    return t;
                }
            }
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPERIENCE
    // ═══════════════════════════════════════════════════════════════════════

    private static final String MONTH_NAMES_RE =
            "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|" +
                    "Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)";

    private static final Pattern DATE_RANGE_PAT = Pattern.compile(
            "(" + MONTH_NAMES_RE + "[\\s,]+\\d{4}" +
                    "|\\d{1,2}/\\d{4}" +
                    "|\\d{4}[-–]\\d{2}" +
                    "|\\d{4})" +
                    "\\s*[-–—to/]+\\s*" +
                    "(" + MONTH_NAMES_RE + "[\\s,]+\\d{4}" +
                    "|\\d{1,2}/\\d{4}" +
                    "|\\d{4}[-–]\\d{2}" +
                    "|\\d{4}" +
                    "|[Pp]resent|[Cc]urrent|[Nn]ow|[Oo]ngoing)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern YEAR_ONLY_PAT = Pattern.compile("\\b(20\\d{2}|19\\d{2})\\b");

    // Work arrangement suffixes that should be stripped from company/role strings
    private static final Pattern WORK_ARRANGEMENT_PAT = Pattern.compile(
            "(?i)[,|]?\\s*(Remote|Hybrid|On-?Site|Contract|Part-?Time|Full-?Time|Freelance)\\s*$");

    private List<Map<String, Object>> extractExperience(Map<String, String> sections) {
        String body = sections.getOrDefault("experience", "")
                + "\n" + sections.getOrDefault("projects", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> jobs = new ArrayList<>();
        String[] blocks = body.split("\n\n+");
        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) continue;
            Map<String, Object> job = parseJobBlock(
                    Arrays.stream(trimmedBlock.split("\n"))
                            .map(String::trim)
                            .filter(l -> !l.isEmpty())
                            .collect(Collectors.toList())
            );
            if (job != null) jobs.add(job);
        }
        return mergeFragments(jobs);
    }

    private Map<String, Object> parseJobBlock(List<String> lines) {
        if (lines.isEmpty()) return null;

        String company = "", role = "", location = "";
        String startDate = "", endDate = "";
        boolean isCurrent = false;
        List<String> bullets = new ArrayList<>();

        // Step 1: Find date range
        int dateLineIdx = -1;
        Matcher dateMatch = null;
        for (int i = 0; i < Math.min(6, lines.size()); i++) {
            Matcher m = DATE_RANGE_PAT.matcher(lines.get(i));
            if (m.find()) { dateLineIdx = i; dateMatch = m; break; }
        }

        if (dateMatch != null) {
            startDate = normaliseDate(dateMatch.group(1));
            String endRaw = dateMatch.group(2).trim();
            if (endRaw.matches("(?i)present|current|now|ongoing")) {
                isCurrent = true; endDate = "";
            } else {
                endDate = normaliseDate(endRaw);
            }
        }

        // Step 2: Extract company, role, location from header lines
        int headerEnd = dateLineIdx >= 0 ? dateLineIdx : Math.min(3, lines.size());
        List<String> headerLines = new ArrayList<>();
        for (int i = 0; i < headerEnd; i++) {
            String l = lines.get(i).trim();
            if (l.isEmpty() || DATE_RANGE_PAT.matcher(l).find()) continue;
            headerLines.add(l);
        }
        if (dateLineIdx >= 0) {
            String dateLine = lines.get(dateLineIdx);
            String stripped = dateLine.replaceAll(DATE_RANGE_PAT.pattern(), "").trim()
                    .replaceAll("[\\s|,–\\-]+$", "").trim();
            if (!stripped.isEmpty()) headerLines.add(stripped);
        }

        if (!headerLines.isEmpty()) {
            String[] rc = assignRoleAndCompany(headerLines);
            role = rc[0]; company = rc[1]; location = rc[2];
        }

        // Step 3: Collect bullet points
        int descStart = dateLineIdx >= 0 ? dateLineIdx + 1 : Math.min(headerLines.size(), lines.size());
        for (int i = descStart; i < lines.size(); i++) {
            String l = lines.get(i).trim()
                    .replaceFirst("^[•●▸▹◦▪\\-–—*>]+\\s*", "");
            if (l.isEmpty() || DATE_RANGE_PAT.matcher(l).find()) continue;
            bullets.add(l);
        }

        if (company.isEmpty() && role.isEmpty()) return null;

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("company",     company);
        job.put("role",        role);
        job.put("location",    location);
        job.put("startDate",   startDate);
        job.put("endDate",     endDate);
        job.put("current",     isCurrent);
        job.put("description", bullets.stream().collect(Collectors.joining("\n• ")));
        return job;
    }

    /**
     * Given 1–3 header lines, determines role, company, and location.
     * Returns String[]{role, company, location}.
     *
     * v2: plain array return (no functional callback); strips work-arrangement
     * suffixes; uses looksLikeCompany/looksLikeTitle for multi-line disambiguation.
     */
    private String[] assignRoleAndCompany(List<String> headerLines) {
        String role = "", company = "", location = "";

        if (headerLines.size() == 1) {
            String h = WORK_ARRANGEMENT_PAT.matcher(headerLines.get(0)).replaceAll("").trim();
            location = extractLocationFromLine(h);

            if (h.contains("@")) {
                String[] parts = h.split("@", 2);
                role    = cleanField(parts[0]);
                company = cleanField(parts[1].replaceAll(LOCATION_PAT.pattern(), ""));
            } else if (h.contains("|")) {
                String[] parts = h.split("\\|");
                String p0 = cleanField(parts[0]);
                String p1 = parts.length > 1 ? cleanField(parts[1]) : "";
                if (looksLikeTitle(p0)) {
                    role    = p0;
                    company = p1.replaceAll(LOCATION_PAT.pattern(), "").trim();
                } else {
                    company = p0.replaceAll(LOCATION_PAT.pattern(), "").trim();
                    role    = p1;
                }
            } else {
                String[] parts = h.split("[,\\-–]", 2);
                if (parts.length >= 2) {
                    String p0 = cleanField(parts[0]);
                    String p1 = cleanField(parts[1]).replaceAll(LOCATION_PAT.pattern(), "").trim();
                    if      (looksLikeTitle(p0)   && !looksLikeTitle(p1))   { role = p0; company = p1; }
                    else if (looksLikeTitle(p1)   && !looksLikeTitle(p0))   { company = p0; role = p1; }
                    else if (looksLikeCompany(p0) && !looksLikeCompany(p1)) { company = p0; role = p1; }
                    else                                                      { role = p0; company = p1; }
                } else {
                    if (looksLikeTitle(h)) role = h; else company = h;
                }
            }
        } else {
            String h0 = WORK_ARRANGEMENT_PAT.matcher(headerLines.get(0)).replaceAll("").trim();
            String h1 = WORK_ARRANGEMENT_PAT.matcher(headerLines.get(1)).replaceAll("").trim();
            location = extractLocationFromLine(h0 + " " + h1);

            String h0c = h0.replaceAll(LOCATION_PAT.pattern(), "").trim().replaceAll("[,|]\\s*$", "").trim();
            String h1c = h1.replaceAll(LOCATION_PAT.pattern(), "").trim().replaceAll("[,|]\\s*$", "").trim();

            boolean h0t = looksLikeTitle(h0c), h1t = looksLikeTitle(h1c);
            boolean h0co = looksLikeCompany(h0c), h1co = looksLikeCompany(h1c);

            if      (h0t  && !h1t)  { role = h0c; company = firstSegment(h1c); }
            else if (h1t  && !h0t)  { role = h1c; company = firstSegment(h0c); }
            else if (h0co && !h1co) { company = firstSegment(h0c); role = h1c; }
            else if (h1co && !h0co) { company = firstSegment(h1c); role = h0c; }
            else                    { role = h0c; company = firstSegment(h1c); }
        }

        // Strip location residue and trailing punctuation
        if (!location.isEmpty()) {
            String locQ = Pattern.quote(location);
            company = company.replaceAll("(?i),?\\s*" + locQ + ".*", "").trim();
            role    = role.replaceAll("(?i),?\\s*" + locQ + ".*", "").trim();
        }
        company = company.replaceAll("[,;|\\-]+$", "").trim();
        role    = role.replaceAll("[,;|\\-]+$", "").trim();

        return new String[]{ role, company, location };
    }

    private String firstSegment(String s) { return s.split("[|@,]")[0].trim(); }

    private String cleanField(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[,;|\\s]+$", "").trim();
    }

    private List<Map<String, Object>> mergeFragments(List<Map<String, Object>> jobs) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            String co = (String) job.getOrDefault("company", "");
            String rl = (String) job.getOrDefault("role",    "");
            if (co.isEmpty() && rl.isEmpty() && !merged.isEmpty()) {
                Map<String, Object> prev = merged.get(merged.size() - 1);
                String prevDesc = (String) prev.getOrDefault("description", "");
                String newDesc  = (String) job.getOrDefault("description", "");
                prev.put("description", (prevDesc + "\n" + newDesc).trim());
            } else {
                merged.add(job);
            }
        }
        return merged;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EDUCATION
    // ═══════════════════════════════════════════════════════════════════════

    private static final LinkedHashMap<String, String> DEGREE_MAP = new LinkedHashMap<>();
    static {
        DEGREE_MAP.put("(?i)\\bb\\.?\\s*tech\\.?\\b|bachelor\\s+of\\s+technology",   "Bachelor of Science");
        DEGREE_MAP.put("(?i)\\bb\\.?\\s*eng\\.?\\b|bachelor\\s+of\\s+engineering",   "Bachelor of Science");
        DEGREE_MAP.put("(?i)\\bb\\.?\\s*sc?\\.?\\b|bachelor\\s+of\\s+science|bs\\b|bsc\\b|b\\.sc\\b", "Bachelor of Science");
        DEGREE_MAP.put("(?i)\\bb\\.?\\s*a\\.?\\b|bachelor\\s+of\\s+arts|ba\\b",       "Bachelor of Arts");
        DEGREE_MAP.put("(?i)\\bb\\.?\\s*e\\.?\\b|bachelor\\s+of\\s+engineering(?!\\s+and)", "Bachelor of Science");
        DEGREE_MAP.put("(?i)\\bm\\.?\\s*tech\\.?\\b|master\\s+of\\s+technology",     "Master of Science");
        DEGREE_MAP.put("(?i)\\bm\\.?\\s*eng\\.?\\b|master\\s+of\\s+engineering",     "Master of Science");
        DEGREE_MAP.put("(?i)\\bm\\.?\\s*sc?\\.?\\b|master\\s+of\\s+science|ms\\b|msc\\b|m\\.sc\\b", "Master of Science");
        DEGREE_MAP.put("(?i)\\bm\\.?\\s*a\\.?\\b|master\\s+of\\s+arts|ma\\b",         "Master of Arts");
        DEGREE_MAP.put("(?i)\\bm\\.?\\s*b\\.?\\s*a\\.?\\b|master\\s+of\\s+business|mba\\b", "MBA");
        DEGREE_MAP.put("(?i)\\bph\\.?\\s*d\\.?\\b|doctorate|doctor\\s+of\\s+philosophy", "PhD");
        DEGREE_MAP.put("(?i)associate'?s?\\s+(?:of\\s+)?(?:science|arts|degree?)|a\\.?s\\.?\\b|a\\.?a\\.?\\b", "Associate Degree");
        DEGREE_MAP.put("(?i)\\bdiploma\\b",                                            "Diploma");
    }

    private static final Pattern GPA_PAT = Pattern.compile(
            "(?:gpa|grade\\s+point|cumulative\\s+gpa)[:\\s]*([0-9]\\.[0-9]{1,2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GPA_FRACTION_PAT = Pattern.compile(
            "([3-9]\\.[0-9]{1,2})\\s*/\\s*([34]\\.[0-9]{0,2})");
    private static final Pattern GRAD_YEAR_PAT = Pattern.compile(
            "(?:graduated?|class\\s+of|expected|anticipated|\\bgrad\\.?)?[:\\s]*(20[0-9]{2}|19[0-9]{2})",
            Pattern.CASE_INSENSITIVE);

    private List<Map<String, Object>> extractEducation(Map<String, String> sections) {
        String body = sections.getOrDefault("education", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> degrees = new ArrayList<>();
        String[] blocks = body.split("\n\n+");
        for (String block : blocks) {
            List<String> blockLines = Arrays.stream(block.split("\n"))
                    .map(String::trim).filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            if (blockLines.isEmpty()) continue;
            Map<String, Object> edu = parseEducationBlock(blockLines);
            if (edu != null) degrees.add(edu);
        }
        if (degrees.isEmpty()) {
            List<String> blockLines = Arrays.stream(body.split("\n"))
                    .map(String::trim).filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            Map<String, Object> edu = parseEducationBlock(blockLines);
            if (edu != null) degrees.add(edu);
        }
        return degrees;
    }

    private Map<String, Object> parseEducationBlock(List<String> lines) {
        if (lines.isEmpty()) return null;

        String fullText = String.join(" ", lines);
        String institution = "", degree = "", field = "", location = "", year = "", gpa = "";

        // Degree
        for (Map.Entry<String, String> entry : DEGREE_MAP.entrySet()) {
            if (Pattern.compile(entry.getKey()).matcher(fullText).find()) {
                degree = entry.getValue(); break;
            }
        }

        // GPA
        Matcher gpaM = GPA_PAT.matcher(fullText);
        if (gpaM.find()) {
            gpa = gpaM.group(1);
        } else {
            Matcher fracM = GPA_FRACTION_PAT.matcher(fullText);
            if (fracM.find()) gpa = fracM.group(1) + " / " + fracM.group(2);
        }

        // Graduation year
        Matcher yearM = GRAD_YEAR_PAT.matcher(fullText);
        if (yearM.find()) year = yearM.group(1);

        // Institution — first line, strip degree abbreviations and split on commas/dashes
        String firstLine = lines.get(0);
        institution = firstLine
                .replaceAll("(?i)\\b(B\\.?S\\.?C?\\.?|B\\.?A\\.?|M\\.?S\\.?C?\\.?|M\\.?A\\.?|M\\.?B\\.?A\\.?|" +
                        "Ph\\.?D\\.?|B\\.?Tech|M\\.?Tech|B\\.?E\\.?|M\\.?E\\.?)\\b", "")
                .replaceAll("[–—]", ",")
                .split(",")[0].trim()
                .replaceAll("[,;|]$", "").trim();

        // Location — Strategy 1: comma-split first line: "University, Austin, TX 2022"
        String[] instParts = firstLine.replaceAll("[–—]", ",").split(",");
        if (instParts.length >= 3) {
            String possCity = instParts[instParts.length - 2].trim();
            String possSt   = instParts[instParts.length - 1].trim()
                    .replaceAll("\\s*\\d{4}.*", "")
                    .replaceAll("(?i)\\s*(expected|anticipated).*", "").trim();
            boolean validState = possSt.matches("[A-Z]{2}")
                    || (possSt.length() > 3 && Character.isUpperCase(possSt.charAt(0)));
            if (validState && possCity.length() >= 2
                    && !KNOWN_SKILLS_LOWER.contains(possCity.toLowerCase())) {
                location = possCity + ", " + possSt;
            }
        }
        // Location — Strategy 2: regex scan across first 4 lines
        if (location.isEmpty()) {
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                Matcher locM = LOCATION_PAT.matcher(lines.get(i));
                if (locM.find()) {
                    String possCity = locM.group(1).trim();
                    if (!KNOWN_SKILLS_LOWER.contains(possCity.toLowerCase())) {
                        location = possCity + ", " + locM.group(2).trim(); break;
                    }
                }
            }
        }

        // Field of study
        Matcher fieldM = Pattern.compile(
                "(?:in|major(?:ing)?\\s+in|concentration\\s+in|of|:)\\s+" +
                        "([A-Z][a-zA-Z\\s&,/]+?)(?:\\s*,|\\s*\\(|\\s*\\d{4}|\\s*-|\\.|$)",
                Pattern.CASE_INSENSITIVE).matcher(fullText);
        while (fieldM.find()) {
            String candidate = fieldM.group(1).trim();
            if (candidate.length() > 3
                    && !candidate.equalsIgnoreCase("science")
                    && !candidate.equalsIgnoreCase("arts")
                    && !candidate.equalsIgnoreCase("business")
                    && !candidate.equalsIgnoreCase("technology")) {
                field = candidate; break;
            }
        }
        if (field.isEmpty() && lines.size() > 1) {
            for (int i = 1; i < Math.min(4, lines.size()); i++) {
                String ln = lines.get(i).trim();
                if (ln.isEmpty() || ln.matches(".*\\d{4}.*")) continue;
                if (LOCATION_PAT.matcher(ln).find()) continue;
                if (ln.length() > 5 && ln.split("\\s+").length <= 6) {
                    field = ln.split(",")[0].trim(); break;
                }
            }
        }

        if (institution.isEmpty()) return null;

        Map<String, Object> edu = new LinkedHashMap<>();
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
     * 250+ known skills. List is ordered longest-first within each category so
     * "React.js" matches before "React", and "Spring Boot" before "Spring".
     * Single-char / short skills use strict whitespace fencing to avoid false matches.
     */
    private static final List<String> KNOWN_SKILLS = Arrays.asList(
            // Languages
            "TypeScript","JavaScript","Python","Java","Kotlin","Swift","Golang","Go",
            "Rust","Scala","C++","C#","PHP","Ruby","MATLAB","Perl","Haskell","Lua",
            "Dart","Groovy","Elixir","Clojure","F#","Erlang","COBOL","Fortran","Objective-C",
            "R",
            // Frontend
            "React Native","React.js","Next.js","Vue.js","AngularJS","Angular","Nuxt.js",
            "Ember.js","React","Vue","Svelte","jQuery",
            "HTML5","CSS3","Tailwind CSS","SASS","SCSS","LESS","Bootstrap",
            "Material UI","Ant Design","Redux","MobX","Zustand","Recoil",
            "GraphQL","Apollo","Webpack","Vite","Babel","ESLint","Prettier","Storybook",
            "HTML","CSS",
            // Backend
            "Spring Boot","Node.js","Express.js","NestJS","FastAPI","Ruby on Rails",
            "ASP.NET","Spring","Express","Django","Flask","Laravel","Rails",
            "Gin","Fiber","Actix","Hapi","Koa","Fastify",
            "gRPC","WebSockets","Microservices","REST API","RESTful API","REST",
            "API Gateway","Serverless",
            // Databases
            "PostgreSQL","SQL Server","Oracle DB","MariaDB","MySQL","SQLite","MSSQL",
            "DynamoDB","MongoDB","Cassandra","Redis","Memcached","Neo4j","CouchDB",
            "Firebase","Firestore","Supabase","PlanetScale","Elasticsearch",
            "Prisma","Hibernate","SQLAlchemy","JDBC","JPA","Sequelize","Mongoose",
            "InfluxDB","TimescaleDB",
            // Cloud & DevOps
            "Google Cloud","AWS","Azure","GCP","Heroku","Vercel","Netlify","DigitalOcean",
            "Kubernetes","Docker","Helm","Terraform","Ansible","Puppet","Chef",
            "GitHub Actions","GitLab CI","CircleCI","Travis CI","ArgoCD","Jenkins",
            "Prometheus","Grafana","Datadog","New Relic","Splunk",
            "ELK Stack","Logstash","Kibana","Linux","Ubuntu","Nginx","Apache",
            "CloudFormation","Pulumi","Vagrant","Packer","CDK",
            // Data / ML / AI
            "TensorFlow","PyTorch","Keras","Scikit-learn","Pandas","NumPy","SciPy",
            "Matplotlib","Seaborn","OpenCV","NLTK","spaCy","Hugging Face","LangChain",
            "LLM","NLP","Computer Vision","Machine Learning","Deep Learning",
            "Data Analysis","Data Science","Data Engineering",
            "Apache Spark","Apache Kafka","Apache Airflow",
            "Spark","Kafka","Airflow","dbt","Snowflake","BigQuery","Redshift","Databricks",
            "Tableau","Power BI","Looker","Metabase","Qlik",
            // Mobile
            "iOS","Android","Flutter","Xamarin","Ionic","Capacitor",
            "SwiftUI","Jetpack Compose","Retrofit","Alamofire",
            // Testing
            "Testing Library","Jest","Cypress","Playwright","Selenium","Mocha","Chai",
            "JUnit","TestNG","Pytest","RSpec","Cucumber","Postman","Insomnia","k6",
            "Locust","Vitest",
            // Security
            "OAuth2","SSL/TLS","JWT","OAuth","SAML","SSL","TLS","OWASP",
            "Penetration Testing","Cybersecurity","Zero Trust","IAM","HashiCorp Vault",
            // Tools / Platforms
            "GitHub Actions","GitHub","GitLab","Bitbucket","Git",
            "JIRA","Confluence","Notion","VS Code","IntelliJ","Eclipse","Xcode",
            "Figma","Sketch","Adobe XD","Swagger","OpenAPI",
            "Maven","Gradle","npm","yarn","pnpm","pip","Poetry","Cargo","Make","CMake",
            "Nx","Turborepo",
            // Methodologies
            "Microservices Architecture","Domain-Driven Design","Design Patterns",
            "Agile","Scrum","Kanban","SAFe","TDD","BDD","CI/CD","DevOps","GitOps",
            "SOLID","Code Review","Technical Writing","Mentoring",
            "Project Management","Leadership","Team Collaboration","Problem Solving"
    );

    private static final Set<String> KNOWN_SKILLS_LOWER =
            KNOWN_SKILLS.stream().map(String::toLowerCase).collect(Collectors.toSet());

    // Skills that need strict whitespace/boundary fencing (single-char or special syntax)
    private static final Set<String> STRICT_BOUNDARY_SKILLS = new HashSet<>(Arrays.asList(
            "R", "Go", "C#", "C++", "F#"
    ));

    private List<String> extractSkills(Map<String, String> sections, String fullText) {
        String skillsBody = sections.getOrDefault("skills", "");
        Set<String> found = new LinkedHashSet<>();

        // Strategy 1: Parse skills section tokens
        if (!skillsBody.isBlank()) {
            for (String line : skillsBody.split("\n")) {
                String cleaned = line.trim()
                        .replaceFirst("^[•●▸▹◦▪\\-–*]+\\s*", "")
                        .replaceFirst("^[A-Za-z\\s&/()]+:\\s*", "");
                String[] tokens = cleaned.split("[,;|/•●]+");
                for (String tok : tokens) {
                    String s = tok.trim().replaceAll("[()\\[\\]]", "").trim();
                    if (s.length() < 2 || s.length() > 60) continue;
                    found.add(normaliseSkillCase(s));
                }
            }
        }

        // Strategy 2: Match known skills with correct word boundaries
        String scanTarget = skillsBody + "\n" + sections.getOrDefault("experience", "");
        for (String skill : KNOWN_SKILLS) {
            if (found.stream().anyMatch(f -> f.equalsIgnoreCase(skill))) continue;

            String escapedSkill = Pattern.quote(skill);
            Pattern pat;

            if (STRICT_BOUNDARY_SKILLS.contains(skill) || skill.length() <= 2) {
                // Strict: must be surrounded by whitespace, comma, newline, or string boundary
                pat = Pattern.compile(
                        "(?:(?<=^)|(?<=[\\s,;|/\n]))" + escapedSkill + "(?=$|[\\s,;|/\n])",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            } else if (skill.contains(".") || skill.contains("+") || skill.contains("#")) {
                // Contains special regex chars — use non-word char lookahead/behind
                pat = Pattern.compile(
                        "(?<![a-zA-Z0-9\\-])" + escapedSkill + "(?![a-zA-Z0-9\\-])",
                        Pattern.CASE_INSENSITIVE);
            } else {
                pat = Pattern.compile("(?i)\\b" + escapedSkill + "\\b");
            }

            if (pat.matcher(scanTarget).find()) found.add(skill);
        }

        return new ArrayList<>(found);
    }

    private String normaliseSkillCase(String s) {
        for (String known : KNOWN_SKILLS) {
            if (known.equalsIgnoreCase(s)) return known;
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CERTIFICATIONS
    // ═══════════════════════════════════════════════════════════════════════

    private static final Set<String> CERT_KEYWORDS = new HashSet<>(Arrays.asList(
            "aws","azure","gcp","google","cisco","comptia","certified","certification",
            "certificate","pmp","pmi","scrum","safe","cka","ckad","ceh","cissp","cism",
            "oscp","ccna","ccnp","rhcsa","rhce","oracle","salesforce","microsoft",
            "hashicorp","terraform","kubernetes","docker","meta","ibm","coursera",
            "udemy","linkedin","coursework","credential"
    ));

    private List<Map<String, Object>> extractCertifications(Map<String, String> sections) {
        String body = sections.getOrDefault("certifications", "");
        if (body.isBlank()) return Collections.emptyList();

        List<Map<String, Object>> certs = new ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim().replaceFirst("^[•●▸\\-–*>]+\\s*", "");
            if (trimmed.length() < 5) continue;

            String lower = trimmed.toLowerCase();
            if (CERT_KEYWORDS.stream().noneMatch(lower::contains)) continue;

            Matcher yearM = Pattern.compile("\\b(20[0-9]{2}|19[0-9]{2})\\b").matcher(trimmed);
            String year = yearM.find() ? yearM.group(1) : "";

            String issuer = "";
            Matcher issuerM = Pattern.compile(
                    "(?:(?:by|from|issued\\s+by)\\s*[–:,]?|[–|])\\s*([A-Z][a-zA-Z0-9\\s&,.]+?)(?:\\s*\\(|\\s*\\d{4}|$)",
                    Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (issuerM.find()) issuer = issuerM.group(1).trim();

            String certName = trimmed
                    .replaceAll("\\b(20[0-9]{2}|19[0-9]{2})\\b", "")
                    .replaceAll("(?i)(by|from|issued\\s+by)\\s+" + Pattern.quote(issuer), "")
                    .replaceAll("[–|,]\\s*" + Pattern.quote(issuer), "")
                    .replaceAll("[,|–]\\s*$", "").trim();

            if (certName.isEmpty()) continue;

            Map<String, Object> cert = new LinkedHashMap<>();
            cert.put("name",   certName);
            cert.put("issuer", issuer);
            cert.put("year",   year);
            certs.add(cert);
        }
        return certs;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATE NORMALISATION  →  "YYYY-MM"
    // ═══════════════════════════════════════════════════════════════════════

    private static final Map<String, String> MONTH_NUM = new LinkedHashMap<>();
    static {
        String[][] months = {
                {"jan","01"},{"feb","02"},{"mar","03"},{"apr","04"},
                {"may","05"},{"jun","06"},{"jul","07"},{"aug","08"},
                {"sep","09"},{"oct","10"},{"nov","11"},{"dec","12"},
                // Quarters and seasons (approximate mid-point)
                {"q1","01"},{"q2","04"},{"q3","07"},{"q4","10"},
                {"spring","03"},{"summer","06"},{"fall","09"},{"autumn","09"},{"winter","12"}
        };
        for (String[] m : months) MONTH_NUM.put(m[0], m[1]);
    }

    private String normaliseDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        raw = raw.trim();

        // Already YYYY-MM
        if (raw.matches("\\d{4}-\\d{2}")) return raw;

        // YYYY only
        if (raw.matches("\\d{4}")) return raw + "-01";

        // MM/YYYY or M/YYYY
        Matcher mmYyyy = Pattern.compile("(\\d{1,2})/(\\d{4})").matcher(raw);
        if (mmYyyy.find()) {
            return mmYyyy.group(2) + "-" + String.format("%02d", Integer.parseInt(mmYyyy.group(1)));
        }

        // YYYY-MM or YYYY–MM (ISO-like with em-dash variant)
        Matcher isoLike = Pattern.compile("(\\d{4})[\\-–](\\d{2})(?!\\d)").matcher(raw);
        if (isoLike.find()) {
            return isoLike.group(1) + "-" + isoLike.group(2);
        }

        // "Jan 2020" / "January, 2020" / "Jan. 2020"
        Matcher monYear = Pattern.compile(
                "(" + MONTH_NAMES_RE + ")[\\s,.]*?(\\d{4})",
                Pattern.CASE_INSENSITIVE).matcher(raw);
        if (monYear.find()) {
            String key = monYear.group(1).substring(0, 3).toLowerCase();
            return monYear.group(2) + "-" + MONTH_NUM.getOrDefault(key, "01");
        }

        // "Q2 2022" / "Q2/2022"
        Matcher quarter = Pattern.compile("(?i)(Q[1-4])[\\s/]*(\\d{4})").matcher(raw);
        if (quarter.find()) {
            String key = quarter.group(1).toLowerCase();
            return quarter.group(2) + "-" + MONTH_NUM.getOrDefault(key, "01");
        }

        // "Spring 2023" / "Fall 2022"
        Matcher season = Pattern.compile(
                "(?i)(Spring|Summer|Fall|Autumn|Winter)[\\s,]*(\\d{4})").matcher(raw);
        if (season.find()) {
            String key = season.group(1).toLowerCase();
            return season.group(2) + "-" + MONTH_NUM.getOrDefault(key, "01");
        }

        // Last resort: extract any 4-digit year
        Matcher yearOnly = YEAR_ONLY_PAT.matcher(raw);
        if (yearOnly.find()) return yearOnly.group(1) + "-01";

        return raw;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HEURISTIC HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private boolean looksLikeCompany(String line) {
        if (line == null || line.isEmpty()) return false;
        return Pattern.compile(
                "(?i)\\b(inc\\.?|llc\\.?|ltd\\.?|corp\\.?|co\\.?|plc\\.?|gmbh|" +
                        "group|solutions|services|technologies|systems|labs?|studio|" +
                        "ventures|consulting|global|international|holdings?|enterprises?|" +
                        "partners?|associates?|agency|foundation|institute|university|college)\\b"
        ).matcher(line).find();
    }

    private boolean looksLikeTitle(String line) {
        if (line == null || line.isEmpty()) return false;
        return TITLE_KEYWORDS.matcher(line).find();
    }

    private String extractLocationFromLine(String line) {
        Matcher m = LOCATION_PAT.matcher(line);
        if (m.find()) {
            String city = m.group(1).trim();
            if (!KNOWN_SKILLS_LOWER.contains(city.toLowerCase())) {
                return city + ", " + m.group(2).trim();
            }
        }
        if (Pattern.compile("(?i)\\b(remote|hybrid|on-?site)\\b").matcher(line).find()) {
            return "Remote";
        }
        return "";
    }
}