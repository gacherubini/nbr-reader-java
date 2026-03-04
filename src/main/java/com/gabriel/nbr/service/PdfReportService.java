package com.gabriel.nbr.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

@Service
public class PdfReportService {

    private static final Locale PT_BR = new Locale("pt", "BR");

    public byte[] buildCostTablePdf(String arquivo, BigDecimal cub, Map<String, BigDecimal> table) throws IOException {
        NumberFormat money = NumberFormat.getCurrencyInstance(PT_BR);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;

                y = writeLine(content, bold, 14, margin, y, "Resumo de Custos - NBR QIII");
                y = writeLine(content, regular, 10, margin, y - 6, "Arquivo: " + safe(arquivo));
                y = writeLine(content, regular, 10, margin, y, "CUB informado: " + money.format(cub));
                y = writeLine(content, regular, 10, margin, y, "Data: " + LocalDate.now());

                float tableTop = y - 20;
                float tableWidth = page.getMediaBox().getWidth() - (margin * 2);
                float rowHeight = 22;
                float leftColWidth = tableWidth * 0.65f;
                float rightColWidth = tableWidth - leftColWidth;

                drawTableHeader(content, bold, margin, tableTop, leftColWidth, rightColWidth, rowHeight);

                float rowY = tableTop - rowHeight;
                for (Map.Entry<String, BigDecimal> entry : table.entrySet()) {
                    drawRow(content, regular, margin, rowY, leftColWidth, rightColWidth, rowHeight,
                            entry.getKey(), money.format(entry.getValue()));
                    rowY -= rowHeight;
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private float writeLine(PDPageContentStream content, PDType1Font font, int size, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (size + 4);
    }

    private void drawTableHeader(PDPageContentStream content, PDType1Font font, float x, float y,
                                 float leftW, float rightW, float h) throws IOException {
        drawRect(content, x, y - h, leftW, h);
        drawRect(content, x + leftW, y - h, rightW, h);
        writeLine(content, font, 10, x + 6, y - 15, "Item");
        writeLine(content, font, 10, x + leftW + 6, y - 15, "Valor");
    }

    private void drawRow(PDPageContentStream content, PDType1Font font, float x, float y,
                         float leftW, float rightW, float h, String item, String value) throws IOException {
        drawRect(content, x, y - h, leftW, h);
        drawRect(content, x + leftW, y - h, rightW, h);
        writeLine(content, font, 10, x + 6, y - 15, item);
        writeLine(content, font, 10, x + leftW + 6, y - 15, value);
    }

    private void drawRect(PDPageContentStream content, float x, float y, float w, float h) throws IOException {
        content.addRect(x, y, w, h);
        content.stroke();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "(sem nome)" : s;
    }
}

