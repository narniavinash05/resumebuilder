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

        if (resume.getCertifications() != null && !resume.getCertifications().isEmpty()) {
            addSection(document, "Certifications", sectionFont);
            addCertifications(document, resume.getCertifications(), normalFont, boldFont);
        }

        document.close();
        return baos.toByteArray();
    }

    private void addHeader(Document document, Resume resume,
                           Font titleFont, Font normalFont) throws Exception {

        Paragraph name = new Paragraph(safe(resume.getFullName()), titleFont);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setLeading(20f);
        name.setSpacingAfter(2f);
        document.add(name);

        ContactInfo c = resume.getContactInfo();
        if (c != null) {

            Paragraph contact = new Paragraph();
            contact.setAlignment(Element.ALIGN_CENTER);
            contact.setLeading(LINE_LEADING);
            contact.setSpacingAfter(4f);

            contact.add(new Chunk(safe(c.getLocation()) + "  |  ", normalFont));
            contact.add(new Chunk(safe(c.getPhone()) + "  |  ", normalFont));

            Anchor email = new Anchor(safe(c.getEmail()), normalFont);
            email.setReference("mailto:" + safe(c.getEmail()));
            contact.add(email);
            contact.add(new Chunk("  |  ", normalFont));

            // Navy blue underlined hyperlink font
            Font linkFont = new Font(normalFont);
            linkFont.setColor(new Color(0, 0, 128));
//            linkFont.setStyle(Font.UNDERLINE);

            Anchor linkedin = new Anchor("LinkedIn", linkFont);
            linkedin.setReference("https://" + safe(c.getLinkedin()));
            contact.add(linkedin);
            contact.add(new Chunk("  |  ", normalFont));

            Anchor portfolio = new Anchor("Portfolio", linkFont);
            portfolio.setReference("https://" + safe(c.getPortfolio()));
            contact.add(portfolio);

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

            addCell(table, safe(exp.getCompany()), bold, Element.ALIGN_LEFT);
            addCell(table,
                    safe(exp.getStartDate()) + " - " + safe(exp.getEndDate()),
                    bold,
                    Element.ALIGN_RIGHT);

            addCell(table, safe(exp.getRole()), normal, Element.ALIGN_LEFT);
            addCell(table, safe(exp.getLocation()), normal, Element.ALIGN_RIGHT);

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

            addCell(table, safe(edu.getInstitution()), bold, Element.ALIGN_LEFT);
            addCell(table, safe(edu.getDuration()), bold, Element.ALIGN_RIGHT);

            addCell(table,
                    safe(edu.getDegree()) +
                            (edu.getGpa() != null ? " (GPA: " + edu.getGpa() + ")" : ""),
                    normal,
                    Element.ALIGN_LEFT);

            addCell(table, safe(edu.getLocation()), normal, Element.ALIGN_RIGHT);

            document.add(table);
        }
    }

    // ----------------------------------------------------
// CERTIFICATIONS
// ----------------------------------------------------
    private void addCertifications(Document document,
                                   List<Certification> certifications,
                                   Font normal,
                                   Font bold) throws Exception {

        if (certifications == null || certifications.isEmpty()) {
            return;
        }

        int columns = 2; // number of certifications per row
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);
        table.setSpacingAfter(2f);

        for (Certification cert : certifications) {

            Phrase phrase = new Phrase();

            // Bullet
            phrase.add(new Chunk("\u2022 ", normal));

            // Name
            phrase.add(new Chunk(safe(cert.getName()), bold));

            // Space
            phrase.add(new Chunk("  ", normal));

            // Hyperlink
            if (cert.getCertificateUrl() != null &&
                    !cert.getCertificateUrl().isBlank()) {

                Font linkFont = new Font(normal);
                linkFont.setColor(new Color(0, 0, 128));

                Anchor link = new Anchor("Certificate", linkFont);
                link.setReference(cert.getCertificateUrl());
                phrase.add(link);
            }

            PdfPCell cell = new PdfPCell(phrase);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(2f);

            table.addCell(cell);
        }

        // If odd number, fill last cell
        if (certifications.size() % columns != 0) {
            PdfPCell empty = new PdfPCell(new Phrase(""));
            empty.setBorder(Rectangle.NO_BORDER);
            table.addCell(empty);
        }

        document.add(table);
    }



    private void addCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setHorizontalAlignment(alignment);
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
