package com.gabriel.nbr.service;

import com.gabriel.nbr.dto.cronograma.LinhaCronogramaResponse;
import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportServiceTest {

    private static final Locale PT_BR = new Locale("pt", "BR");

    private final PdfReportService pdfReportService = new PdfReportService();

    @Test
    void shouldRenderCronogramaMonthByMonthWithFinalTotal() throws IOException {
        MatrizCronogramaResponse cronograma = new MatrizCronogramaResponse(
                List.of("2026-01", "2026-02", "2026-03"),
                List.of(
                        new LinhaCronogramaResponse("Terreno", List.of(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("250.00"))),
                        new LinhaCronogramaResponse("Fundacao", List.of(new BigDecimal("50.00"), new BigDecimal("25.00"), new BigDecimal("75.00")))
                )
        );

        byte[] pdf = pdfReportService.buildCronogramaPdf("obra.xlsx", new BigDecimal("3500.00"), cronograma);
        String text = extractText(pdf);

        NumberFormat money = NumberFormat.getCurrencyInstance(PT_BR);
        String jan = money.format(new BigDecimal("150.00"));
        String fev = money.format(new BigDecimal("25.00"));
        String mar = money.format(new BigDecimal("325.00"));
        String total = money.format(new BigDecimal("500.00"));
        String totalTerreno = money.format(new BigDecimal("350.00"));
        String totalFundacao = money.format(new BigDecimal("150.00"));

        assertTrue(text.contains("Cronograma Fisico-Financeiro"));
        assertTrue(text.contains("Gastos separados por categoria"));
        assertTrue(text.contains("Terreno - Total: " + totalTerreno));
        assertTrue(text.contains("Fundacao - Total: " + totalFundacao));
        assertTrue(text.contains("Totais somados mes a mes"));
        assertTrue(text.contains("Mes"));
        assertTrue(text.contains("Valor no mes"));
        assertTrue(text.contains("2026-01"));
        assertTrue(text.contains("2026-02"));
        assertTrue(text.contains("2026-03"));
        assertTrue(text.contains(jan));
        assertTrue(text.contains(fev));
        assertTrue(text.contains(mar));
        assertTrue(text.contains("Total do cronograma"));
        assertTrue(text.contains(total));
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
