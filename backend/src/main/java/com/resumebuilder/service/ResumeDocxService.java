package com.resumebuilder.service;

import com.resumebuilder.model.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Generates an ATS-compatible .docx resume that exactly mirrors the PDF layout:
 *   Name (centered, bold, 18pt)
 *   Contact line (centered, 11pt)  ── separator line
 *   Section headers (bold, 13pt, navy)
 *   Experience / Education / Skills / Certifications
 *
 * Uses Apache POI (already on the Spring Boot classpath via spring-boot-starter-data-jpa
 * → poi-ooxml transitive dep).  If poi-ooxml is not yet declared explicitly, add:
 *
 *   <dependency>
 *     <groupId>org.apache.poi</groupId>
 *     <artifactId>poi-ooxml</artifactId>
 *     <version>5.2.5</version>
 *   </dependency>
 */
@Service
public class ResumeDocxService {

    // ── Colour constants (hex strings for POI) ────────────────────────────────
    private static final String NAVY  = "000080";   // section headers – matches PDF navy
    private static final String BLACK = "000000";
    private static final String GRAY  = "555555";   // contact / location lines

    // ── Font sizes (half-points) ──────────────────────────────────────────────
    private static final int SZ_NAME    = 36;  // 18 pt
    private static final int SZ_CONTACT = 22;  // 11 pt
    private static final int SZ_SECTION = 26;  // 13 pt
    private static final int SZ_BODY    = 22;  // 11 pt (matches PDF BASE_FONT_SIZE = 11)

    // ── Spacing (twips) ───────────────────────────────────────────────────────
    private static final int SPACE_BEFORE_SECTION = 160;  // ~8 pt
    private static final int SPACE_AFTER_SECTION  =  80;  // ~4 pt
    private static final int SPACE_AFTER_BODY     = 120;  // ~6 pt

    // ── Main entry point ─────────────────────────────────────────────────────
    public byte[] generateResume(Resume resume) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            setPageMargins(doc);

            addName(doc, safe(resume.getFullName()));
            addContactLine(doc, resume.getContactInfo());
            addHorizontalRule(doc);

            addSectionHeader(doc, "Professional Summary");
            addBodyParagraph(doc, safe(resume.getProfessionalSummary()));

            if (hasItems(resume.getExperiences())) {
                addSectionHeader(doc, "Experience");
                addExperiences(doc, resume.getExperiences());
            }

            if (hasItems(resume.getSkillCategories())) {
                addSectionHeader(doc, "Skills");
                addSkillCategories(doc, resume.getSkillCategories());
            }

            if (hasItems(resume.getEducation())) {
                addSectionHeader(doc, "Education");
                addEducation(doc, resume.getEducation());
            }

            if (hasItems(resume.getCertifications())) {
                addSectionHeader(doc, "Certifications");
                addCertifications(doc, resume.getCertifications());
            }

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // ── Page setup ────────────────────────────────────────────────────────────
    private void setPageMargins(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageMar mar = sectPr.addNewPgMar();
        // US Letter, 0.25-inch margins (matches PDF: 18pt ≈ 0.25 in → 360 twips)
        mar.setTop(BigInteger.valueOf(360));
        mar.setBottom(BigInteger.valueOf(360));
        mar.setLeft(BigInteger.valueOf(360));
        mar.setRight(BigInteger.valueOf(360));
    }

    // ── Header: candidate name ────────────────────────────────────────────────
    private void addName(XWPFDocument doc, String name) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setSpacing(p, 0, 40);

        XWPFRun r = p.createRun();
        r.setText(name);
        r.setBold(true);
        r.setFontSize(SZ_NAME / 2);
        r.setFontFamily("Lato");
        r.setColor(BLACK);
    }

    // ── Header: contact line ──────────────────────────────────────────────────
    private void addContactLine(XWPFDocument doc, ContactInfo c) {
        if (c == null) return;
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        setSpacing(p, 0, 80);

        // Build: location  |  phone  |  email  |  linkedin  |  portfolio
        String[] parts = {
                safe(c.getLocation()),
                safe(c.getPhone()),
                safe(c.getEmail()),
                safe(c.getLinkedin()),
                safe(c.getPortfolio())
        };

        boolean first = true;
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!first) addRun(p, "  |  ", SZ_CONTACT, false, GRAY);
            addRun(p, part, SZ_CONTACT, false, GRAY);
            first = false;
        }
    }

    // ── Horizontal rule (paragraph with bottom border) ────────────────────────
    private void addHorizontalRule(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, 0, 80);
        CTPPr ppr = getOrCreatePPr(p);
        CTPBdr bdr = ppr.addNewPBdr();
        CTBorder bottom = bdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(4));      // 0.5 pt
        bottom.setColor(BLACK);
        bottom.setSpace(BigInteger.valueOf(1));
    }

    // ── Section header (navy, bold, 13 pt) ───────────────────────────────────
    private void addSectionHeader(XWPFDocument doc, String title) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, SPACE_BEFORE_SECTION, SPACE_AFTER_SECTION);
        XWPFRun r = p.createRun();
        r.setText(title);
        r.setBold(true);
        r.setFontSize(SZ_SECTION / 2);
        r.setFontFamily("Lato");
        r.setColor(NAVY);
    }

    // ── Body paragraph (normal text) ──────────────────────────────────────────
    private void addBodyParagraph(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) return;
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, 0, SPACE_AFTER_BODY);
        addRun(p, text, SZ_BODY, false, BLACK);
    }

    // ── Experience ────────────────────────────────────────────────────────────
    private void addExperiences(XWPFDocument doc, List<Experience> experiences) {
        for (Experience exp : experiences) {
            // Row 1: Company (left) | Date range (right)  — tab stop
            XWPFParagraph row1 = doc.createParagraph();
            setSpacing(row1, 40, 0);
            setRightTabStop(row1);
            addRun(row1, safe(exp.getCompany()), SZ_BODY, true, BLACK);
            addTabRun(row1, formatDateRange(exp.getStartDate(), exp.getEndDate()), SZ_BODY, true, BLACK);

            // Row 2: Role (left) | Location (right)
            XWPFParagraph row2 = doc.createParagraph();
            setSpacing(row2, 0, 40);
            setRightTabStop(row2);
            addRun(row2, safe(exp.getRole()), SZ_BODY, false, GRAY);
            addTabRun(row2, safe(exp.getLocation()), SZ_BODY, false, GRAY);

            // Bullet list
            if (exp.getResponsibilities() != null) {
                for (String bullet : exp.getResponsibilities()) {
                    addBullet(doc, safe(bullet));
                }
            }
        }
    }

    // ── Skill categories ──────────────────────────────────────────────────────
    private void addSkillCategories(XWPFDocument doc, List<SkillCategory> categories) {
        for (SkillCategory cat : categories) {
            if (cat == null || !hasItems(cat.getSkills())) continue;
            XWPFParagraph p = doc.createParagraph();
            setSpacing(p, 0, 80);
            addRun(p, safe(cat.getCategoryName()) + ": ", SZ_BODY, true, BLACK);
            addRun(p, String.join(", ", cat.getSkills()), SZ_BODY, false, BLACK);
        }
    }

    // ── Education ─────────────────────────────────────────────────────────────
    private void addEducation(XWPFDocument doc, List<Education> educationList) {
        for (Education edu : educationList) {
            XWPFParagraph row1 = doc.createParagraph();
            setSpacing(row1, 40, 0);
            setRightTabStop(row1);
            addRun(row1, safe(edu.getInstitution()), SZ_BODY, true, BLACK);
            addTabRun(row1, safe(edu.getDuration()), SZ_BODY, true, BLACK);

            XWPFParagraph row2 = doc.createParagraph();
            setSpacing(row2, 0, 80);
            String degreeText = safe(edu.getDegree())
                    + (edu.getGpa() != null && !edu.getGpa().isBlank()
                    ? " (GPA: " + edu.getGpa() + ")" : "");
            addRun(row2, degreeText, SZ_BODY, false, GRAY);
            if (edu.getLocation() != null && !edu.getLocation().isBlank()) {
                addTabRun(row2, safe(edu.getLocation()), SZ_BODY, false, GRAY);
            }
        }
    }

    // ── Certifications ────────────────────────────────────────────────────────
    private void addCertifications(XWPFDocument doc, List<Certification> certifications) {
        for (Certification cert : certifications) {
            addBullet(doc, safe(cert.getName())
                    + (cert.getIssuingOrganization() != null && !cert.getIssuingOrganization().isBlank()
                    ? " — " + cert.getIssuingOrganization() : ""));
        }
    }

    // ── Bullet helper (real list bullet, ATS-safe) ────────────────────────────
    private void addBullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        setSpacing(p, 0, 40);

        CTPPr ppr = getOrCreatePPr(p);
        CTInd ind = ppr.addNewInd();
        ind.setLeft(BigInteger.valueOf(360));    // 0.25 in indent
        ind.setHanging(BigInteger.valueOf(180)); // hanging indent for bullet char

        // Use a plain hyphen-dash as bullet — universally ATS-readable
        XWPFRun bullet = p.createRun();
        bullet.setText("- ");
        bullet.setFontSize(SZ_BODY / 2);
        bullet.setFontFamily("Lato");
        bullet.setColor(BLACK);

        XWPFRun content = p.createRun();
        content.setText(text);
        content.setFontSize(SZ_BODY / 2);
        content.setFontFamily("Lato");
        content.setColor(BLACK);
    }

    // ── Tab stop at right margin ───────────────────────────────────────────────
    private void setRightTabStop(XWPFParagraph p) {
        CTPPr ppr = getOrCreatePPr(p);
        CTTabs tabs = ppr.addNewTabs();
        CTTabStop tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        // Content width for 0.25-in margins on US Letter: 12240 - 720 = 11520 twips
        tab.setPos(BigInteger.valueOf(11520));
    }

    // ── Run helpers ───────────────────────────────────────────────────────────
    private void addRun(XWPFParagraph p, String text, int szHalfPt, boolean bold, String color) {
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontSize(szHalfPt / 2);
        r.setFontFamily("Lato");
        r.setColor(color);
    }

    private void addTabRun(XWPFParagraph p, String text, int szHalfPt, boolean bold, String color) {
        XWPFRun tab = p.createRun();
        tab.addTab();  // inserts a tab character that jumps to the right tab stop

        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontSize(szHalfPt / 2);
        r.setFontFamily("Lato");
        r.setColor(color);
    }

    // ── Spacing helper ────────────────────────────────────────────────────────
    private void setSpacing(XWPFParagraph p, int beforeTwips, int afterTwips) {
        CTPPr ppr = getOrCreatePPr(p);
        CTSpacing spacing = ppr.addNewSpacing();
        spacing.setBefore(BigInteger.valueOf(beforeTwips));
        spacing.setAfter(BigInteger.valueOf(afterTwips));
        spacing.setLine(BigInteger.valueOf(276));          // 1.15× line spacing
        spacing.setLineRule(STLineSpacingRule.AUTO);
    }

    // ── Date helpers ──────────────────────────────────────────────────────────
    private String formatDateRange(String start, String end) {
        String s = formatDate(start);
        String e = (end == null || end.isBlank()) ? "Present" : formatDate(end);
        if (s.isBlank()) return e;
        return s + " - " + e;
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw.trim();
        if (!t.matches("\\d{4}-\\d{2}")) return t;
        try {
            int year  = Integer.parseInt(t.substring(0, 4));
            int month = Integer.parseInt(t.substring(5, 7));
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                    "Jul","Aug","Sep","Oct","Nov","Dec"};
            return months[month - 1] + ", " + year;
        } catch (Exception ex) { return t; }
    }

    // ── Utility helpers ───────────────────────────────────────────────────────
    private CTPPr getOrCreatePPr(XWPFParagraph p) {
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr == null) ppr = p.getCTP().addNewPPr();
        return ppr;
    }

    private String safe(String s) { return s != null ? s : ""; }

    private boolean hasItems(List<?> list) { return list != null && !list.isEmpty(); }
}