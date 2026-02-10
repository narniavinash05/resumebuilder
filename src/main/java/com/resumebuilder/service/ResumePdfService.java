package com.resumebuilder.service;

import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.resumebuilder.model.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.List;

@Service
public class ResumePdfService {

    private static final float BASE_FONT_SIZE = 10f;
    private static final float LINE_LEADING = 12f;

    public byte[] generateResume(Resume resume) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Document document = new Document(PageSize.LETTER, 18, 18, 18, 18);
        PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont latoRegular = loadFont("/fonts/Lato-Regular.ttf");
        BaseFont latoBold = loadFont("/fonts/Lato-Bold.ttf");

        Font titleFont = new Font(latoBold, 18, Font.BOLD);
        Font normalFont = new Font(latoRegular, BASE_FONT_SIZE);
        Font boldFont = new Font(latoBold, BASE_FONT_SIZE);
        Font sectionFont = new Font(latoBold, 13, Font.BOLD, new Color(0, 0, 128));

        addHeader(document, resume, titleFont, normalFont);

        addSection(document, "Professional Summary", sectionFont);
        addSummary(document, resume.getProfessionalSummary(), normalFont);

        if (resume.getExperiences() != null && !resume.getExperiences().isEmpty()) {
            addSection(document, "Experience", sectionFont);
            addExperience(document, resume.getExperiences(), normalFont, boldFont);
        }

        if (resume.getSkillCategories() != null
                && !resume.getSkillCategories().isEmpty()) {

            addSection(document, "Skills", sectionFont);
            addSkillCategories(document,
                    resume.getSkillCategories(),
                    normalFont,
                    boldFont);
        }

        if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
            addSection(document, "Education", sectionFont);
            addEducation(document, resume.getEducation(), normalFont, boldFont);
        }

        document.close();
        return baos.toByteArray();
    }

    // ----------------------------------------------------
    // HEADER
    // ----------------------------------------------------
    private void addHeader(Document document, Resume resume,
                           Font titleFont, Font normalFont) throws Exception {

        Paragraph name = new Paragraph(safe(resume.getFullName()), titleFont);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setLeading(20f);
        name.setSpacingAfter(2f);
        document.add(name);

        ContactInfo c = resume.getContactInfo();
        if (c != null) {
            Paragraph contact = new Paragraph(
                    safe(c.getLocation()) + "  |  " +
                            safe(c.getPhone()) + "  |  " +
                            safe(c.getEmail()),
                    normalFont
            );
            contact.setAlignment(Element.ALIGN_CENTER);
            contact.setLeading(LINE_LEADING);
            contact.setSpacingAfter(4f);
            document.add(contact);
        }

        LineSeparator line = new LineSeparator();
        line.setLineWidth(0.5f);
        document.add(line);
    }

    // ----------------------------------------------------
    // SECTION TITLE
    // ----------------------------------------------------
    private void addSection(Document document, String title, Font sectionFont)
            throws Exception {

        Paragraph section = new Paragraph(title, sectionFont);
        section.setSpacingBefore(8f);
        section.setSpacingAfter(4f);
        section.setLeading(14f);
        document.add(section);
    }

    // ----------------------------------------------------
    // SUMMARY
    // ----------------------------------------------------
    private void addSummary(Document document, String summary, Font normal)
            throws Exception {

        if (summary == null || summary.isBlank()) return;

        Paragraph paragraph = new Paragraph(summary, normal);
        paragraph.setLeading(LINE_LEADING);
        paragraph.setSpacingAfter(6f);
        document.add(paragraph);
    }

    // ----------------------------------------------------
    // EXPERIENCE
    // ----------------------------------------------------
    private void addExperience(Document document,
                               List<Experience> experiences,
                               Font normal,
                               Font bold) throws Exception {

        for (Experience exp : experiences) {

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(2f);
            table.setSpacingAfter(2f);
            table.setWidths(new float[]{70, 30});

            addCell(table, safe(exp.getCompany()), bold);
            addCell(table,
                    safe(exp.getStartDate()) + " - " + safe(exp.getEndDate()),
                    bold);

            addCell(table, safe(exp.getRole()), normal);
            addCell(table, safe(exp.getLocation()), normal);

            document.add(table);

            if (exp.getResponsibilities() != null) {

                com.lowagie.text.List bulletList =
                        new com.lowagie.text.List(false, 10f);

                bulletList.setIndentationLeft(15f);
                bulletList.setSymbolIndent(8f);

                for (String bullet : exp.getResponsibilities()) {
                    ListItem item = new ListItem(safe(bullet), normal);
                    item.setLeading(LINE_LEADING);
                    bulletList.add(item);
                }

                document.add(bulletList);
            }
        }
    }

    // ----------------------------------------------------
    // SKILL CATEGORIES (UPDATED)
    // ----------------------------------------------------
    private void addSkillCategories(Document document,
                                    List<SkillCategory> categories,
                                    Font normal,
                                    Font bold) throws Exception {

        if (categories == null || categories.isEmpty()) {
            return;
        }

        for (SkillCategory category : categories) {

            if (category == null
                    || category.getSkills() == null
                    || category.getSkills().isEmpty()) {
                continue;
            }

            Paragraph paragraph = new Paragraph();
            paragraph.setLeading(LINE_LEADING);
            paragraph.setSpacingAfter(4f);

            paragraph.add(new Chunk(
                    safe(category.getCategoryName()) + ": ",
                    bold
            ));

            paragraph.add(new Chunk(
                    String.join(", ", category.getSkills()),
                    normal
            ));

            document.add(paragraph);
        }
    }


    // ----------------------------------------------------
    // EDUCATION
    // ----------------------------------------------------
    private void addEducation(Document document,
                              List<Education> educationList,
                              Font normal,
                              Font bold) throws Exception {

        for (Education edu : educationList) {

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(2f);
            table.setSpacingAfter(4f);
            table.setWidths(new float[]{70, 30});

            addCell(table, safe(edu.getInstitution()), bold);
            addCell(table, safe(edu.getDuration()), bold);

            addCell(table,
                    safe(edu.getDegree()) +
                            (edu.getGpa() != null ? " (GPA: " + edu.getGpa() + ")" : ""),
                    normal);

            addCell(table, safe(edu.getLocation()), normal);

            document.add(table);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        table.addCell(cell);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // ----------------------------------------------------
    // FONT LOADER
    // ----------------------------------------------------
    private BaseFont loadFont(String path) throws Exception {

        URL fontUrl = getClass().getResource(path);
        if (fontUrl == null) {
            throw new RuntimeException("Font not found at " + path);
        }

        return BaseFont.createFont(
                fontUrl.toString(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
        );
    }
}
