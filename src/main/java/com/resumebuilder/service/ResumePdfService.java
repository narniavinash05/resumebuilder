package com.resumebuilder.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.resumebuilder.model.ContactInfo;
import com.resumebuilder.model.Education;
import com.resumebuilder.model.Experience;
import com.resumebuilder.model.Resume;

import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.List;

@Service
public class ResumePdfService {

    public byte[] generateResume(Resume resume) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER);

        PdfWriter.getInstance(document, baos);
        document.open();
        document.setMargins(18f, 18f, 18f, 18f);

        BaseFont latoRegular = loadFont("/fonts/Lato-Regular.ttf");
        BaseFont latoBold = loadFont("/fonts/Lato-Bold.ttf");

        Font titleFont = new Font(latoBold, 18, Font.BOLD);
        Font normalFont = new Font(latoRegular, 10);
        Font boldFont = new Font(latoBold, 10);
        Font sectionFont = new Font(latoBold, 14, Font.BOLD, new Color(0, 0, 128));

        addHeader(document, resume, titleFont, normalFont);

        addSection(document, "Professional Summary", sectionFont);
        addSummary(document, resume.getProfessionalSummary(), normalFont);

        if (resume.getExperiences() != null && !resume.getExperiences().isEmpty()) {
            addSection(document, "Experience", sectionFont);
            addExperience(document, resume.getExperiences(), normalFont, boldFont);
        }

        if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
            addSection(document, "Skills", sectionFont);
            addSkills(document, resume.getSkills(), normalFont, boldFont);
        }

        if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
            addSection(document, "Education", sectionFont);
            addEducation(document, resume.getEducation(), normalFont, boldFont);
        }

        document.close();
        return baos.toByteArray();
    }

    // ----------------------------
    // Header
    // ----------------------------
    private void addHeader(Document document, Resume resume,
                           Font titleFont, Font normalFont) throws Exception {

        Paragraph name = new Paragraph(safe(resume.getFullName()), titleFont);
        name.setAlignment(Element.ALIGN_CENTER);
        document.add(name);

        document.add(Chunk.NEWLINE);

        ContactInfo c = resume.getContactInfo();

        if (c != null) {
            String contactLine = String.join(" | ",
                    safe(c.getLocation()),
                    safe(c.getPhone()),
                    safe(c.getEmail())
            );

            Paragraph contact = new Paragraph(contactLine, normalFont);
            contact.setAlignment(Element.ALIGN_CENTER);
            document.add(contact);
        }

        document.add(Chunk.NEWLINE);
        document.add(new LineSeparator());
        document.add(Chunk.NEWLINE);
    }

    // ----------------------------
    // Section Title
    // ----------------------------
    private void addSection(Document document, String title, Font sectionFont)
            throws Exception {

        Paragraph section = new Paragraph(title, sectionFont);
        section.setSpacingBefore(6f);
        section.setSpacingAfter(4f);
        document.add(section);
    }

    // ----------------------------
    // Summary
    // ----------------------------
    private void addSummary(Document document, String summary, Font normal)
            throws Exception {

        if (summary == null || summary.isBlank()) return;

        Paragraph paragraph = new Paragraph(summary, normal);
        paragraph.setSpacingAfter(8f);
        document.add(paragraph);
    }

    // ----------------------------
    // Experience
    // ----------------------------
    private void addExperience(Document document,
                               List<Experience> experiences,
                               Font normal,
                               Font bold) throws Exception {

        for (Experience exp : experiences) {

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{70, 30});

            addCell(table, safe(exp.getCompany()), bold);
            addCell(table,
                    safe(exp.getStartDate()) + " - " + safe(exp.getEndDate()),
                    bold);

            addCell(table, safe(exp.getRole()), normal);
            addCell(table, safe(exp.getLocation()), normal);

            document.add(table);
            document.add(Chunk.NEWLINE);

            if (exp.getResponsibilities() != null) {
                com.lowagie.text.List bulletList =
                        new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);

                for (String bullet : exp.getResponsibilities()) {
                    bulletList.add(new ListItem(safe(bullet), normal));
                }

                document.add(bulletList);
                document.add(Chunk.NEWLINE);
            }
        }
    }

    // ----------------------------
    // Skills
    // ----------------------------
    private void addSkills(Document document,
                           List<String> skills,
                           Font normal,
                           Font bold) throws Exception {

        Paragraph p = new Paragraph();
        p.add(new Chunk("Skills: ", bold));
        p.add(new Chunk(String.join(", ", skills), normal));
        document.add(p);
    }

    // ----------------------------
    // Education
    // ----------------------------
    private void addEducation(Document document,
                              List<Education> educationList,
                              Font normal,
                              Font bold) throws Exception {

        for (Education edu : educationList) {

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{70, 30});

            addCell(table, safe(edu.getInstitution()), bold);
            addCell(table, safe(edu.getDuration()), bold);

            addCell(table,
                    safe(edu.getDegree()) +
                            (edu.getGpa() != null ? " (GPA: " + edu.getGpa() + ")" : ""),
                    normal);

            addCell(table, safe(edu.getLocation()), normal);

            document.add(table);
            document.add(Chunk.NEWLINE);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // ----------------------------
    // Font Loader (FIXED)
    // ----------------------------
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
