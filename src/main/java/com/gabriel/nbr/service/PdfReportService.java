package com.gabriel.nbr.service;

import com.gabriel.nbr.dto.cronograma.LinhaCronogramaResponse;
import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PdfReportService {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final int MAX_CHARS_POR_LINHA_VALORES = 105;

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
                BigDecimal total = BigDecimal.ZERO;
                for (Map.Entry<String, BigDecimal> entry : table.entrySet()) {
                    BigDecimal value = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                    drawRow(content, regular, margin, rowY, leftColWidth, rightColWidth, rowHeight,
                            entry.getKey(), money.format(value));
                    total = total.add(value);
                    rowY -= rowHeight;
                }

                drawRow(content, bold, margin, rowY, leftColWidth, rightColWidth, rowHeight,
                        "Total", money.format(total));
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    public byte[] buildCronogramaPdf(String arquivo, BigDecimal cub, MatrizCronogramaResponse cronograma) throws IOException {
        NumberFormat money = NumberFormat.getCurrencyInstance(PT_BR);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(landscape);
            document.addPage(page);

            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float margin = 36;
            float y = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                y = writeLine(content, bold, 14, margin, y, "Cronograma Fisico-Financeiro");
                y = writeLine(content, regular, 10, margin, y - 6, "Arquivo: " + safe(arquivo));
                y = writeLine(content, regular, 10, margin, y, "CUB informado: " + money.format(cub));
                y = writeLine(content, regular, 10, margin, y, "Data: " + LocalDate.now());

                List<String> cabecalho = cronograma.cabecalho() == null ? List.of() : cronograma.cabecalho();
                List<LinhaCronogramaResponse> linhas = cronograma.linhas() == null ? List.of() : cronograma.linhas();

                int monthCount = cabecalho.size();
                for (LinhaCronogramaResponse linha : linhas) {
                    List<BigDecimal> valores = linha.valores() == null ? List.of() : linha.valores();
                    monthCount = Math.max(monthCount, valores.size());
                }

                if (monthCount > 0) {
                    String inicio = mesPorIndice(cabecalho, 0);
                    String fim = mesPorIndice(cabecalho, monthCount - 1);
                    y = writeLine(content, regular, 10, margin, y, "Periodo: " + inicio + " ate " + fim);
                }

                BigDecimal[] totaisMensais = new BigDecimal[monthCount];
                for (int i = 0; i < monthCount; i++) {
                    totaisMensais[i] = BigDecimal.ZERO;
                }

                y -= 10;
                y = writeLine(content, bold, 11, margin, y, "Gastos separados por categoria");

                for (LinhaCronogramaResponse linha : linhas) {
                    List<BigDecimal> valores = linha.valores() == null ? List.of() : linha.valores();
                    for (int i = 0; i < valores.size() && i < monthCount; i++) {
                        BigDecimal valor = valores.get(i) == null ? BigDecimal.ZERO : valores.get(i);
                        totaisMensais[i] = totaisMensais[i].add(valor);
                    }

                    BigDecimal totalCategoria = soma(valores);
                    String categoria = safe(linha.categoria());
                    y = writeLine(content, bold, 10, margin, y, categoria + " - Total: " + money.format(totalCategoria));

                    String valoresCompactados = compactarValores(cabecalho, valores, money);
                    for (String linhaValores : quebrarValoresEmLinhas(valoresCompactados)) {
                        y = writeLine(content, regular, 10, margin + 10, y, linhaValores);
                    }
                    y -= 4;
                }

                y -= 6;
                y = writeLine(content, bold, 11, margin, y, "Totais somados mes a mes");

                float tableTop = y - 6;
                float tableWidth = page.getMediaBox().getWidth() - (margin * 2);
                float rowHeight = 22;
                float leftColWidth = tableWidth * 0.45f;
                float rightColWidth = tableWidth - leftColWidth;

                drawTableHeader(content, bold, margin, tableTop, leftColWidth, rightColWidth, rowHeight,
                        "Mes", "Valor no mes");

                float rowY = tableTop - rowHeight;
                BigDecimal totalCronograma = BigDecimal.ZERO;

                for (int i = 0; i < monthCount; i++) {
                    String mes = mesPorIndice(cabecalho, i);
                    BigDecimal totalMes = totaisMensais[i];
                    drawRow(content, regular, margin, rowY, leftColWidth, rightColWidth, rowHeight,
                            mes, money.format(totalMes));
                    totalCronograma = totalCronograma.add(totalMes);
                    rowY -= rowHeight;
                }

                drawRow(content, bold, margin, rowY, leftColWidth, rightColWidth, rowHeight,
                        "Total do cronograma", money.format(totalCronograma));
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
        drawTableHeader(content, font, x, y, leftW, rightW, h, "Item", "Valor");
    }

    private void drawTableHeader(PDPageContentStream content, PDType1Font font, float x, float y,
                                 float leftW, float rightW, float h, String leftLabel, String rightLabel) throws IOException {
        drawRect(content, x, y - h, leftW, h);
        drawRect(content, x + leftW, y - h, rightW, h);
        writeLine(content, font, 10, x + 6, y - 15, leftLabel);
        writeLine(content, font, 10, x + leftW + 6, y - 15, rightLabel);
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

    private BigDecimal soma(List<BigDecimal> valores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            total = total.add(valor == null ? BigDecimal.ZERO : valor);
        }
        return total;
    }

    private String compactarValores(List<String> cabecalho, List<BigDecimal> valores, NumberFormat money) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            BigDecimal valor = valores.get(i);
            if (valor == null || valor.signum() == 0) {
                continue;
            }

            if (!sb.isEmpty()) {
                sb.append(" | ");
            }

            String mes = i < cabecalho.size() ? cabecalho.get(i) : "mes-" + (i + 1);
            sb.append(mes).append(": ").append(money.format(valor));
        }

        return sb.isEmpty() ? "Sem desembolso no periodo." : sb.toString();
    }

    private List<String> quebrarValoresEmLinhas(String valoresCompactados) {
        if (valoresCompactados == null || valoresCompactados.isBlank()) {
            return List.of("Sem desembolso no periodo.");
        }

        String[] partes = valoresCompactados.split(" \\| ");
        if (partes.length <= 1) {
            return List.of(valoresCompactados);
        }

        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();

        for (String parte : partes) {
            if (atual.isEmpty()) {
                atual.append(parte);
                continue;
            }

            if (atual.length() + 3 + parte.length() <= MAX_CHARS_POR_LINHA_VALORES) {
                atual.append(" | ").append(parte);
            } else {
                linhas.add(atual.toString());
                atual = new StringBuilder(parte);
            }
        }

        if (!atual.isEmpty()) {
            linhas.add(atual.toString());
        }

        return linhas;
    }

    private String mesPorIndice(List<String> cabecalho, int index) {
        if (index < 0) {
            return "mes-1";
        }
        return index < cabecalho.size() ? cabecalho.get(index) : "mes-" + (index + 1);
    }
}
