package com.gabriel.nbr.controller;

import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
import com.gabriel.nbr.service.CostCompositionService;
import com.gabriel.nbr.service.PdfArtifactStorageService;
import com.gabriel.nbr.service.PdfReportService;
import com.gabriel.nbr.service.QiiiParserService;
import com.gabriel.nbr.service.cronograma.CronogramaFacadeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private QiiiParserService qiiiParserService;

    @Mock
    private CostCompositionService costCompositionService;

    @Mock
    private PdfReportService pdfReportService;

    @Mock
    private PdfArtifactStorageService pdfArtifactStorageService;

    @Mock
    private CronogramaFacadeService cronogramaFacadeService;

    @InjectMocks
    private UploadController uploadController;

    @Test
    void shouldGeneratePdfOnUploadXlsxEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        Map<String, Object> qiii = new LinkedHashMap<>();
        Map<String, BigDecimal> table = new LinkedHashMap<>();
        table.put("1. Terreno", new BigDecimal("1200000.00"));
        byte[] expectedPdf = new byte[]{1, 2, 3};

        when(qiiiParserService.parseQiii(eq(file), eq(new BigDecimal("3500.00")))).thenReturn(qiii);
        when(costCompositionService.buildCostTable(eq(qiii), eq(new BigDecimal("1200000.00")))).thenReturn(table);
        when(pdfReportService.buildCostTablePdf(eq("qiii.xlsx"), eq(new BigDecimal("3500.00")), eq(table))).thenReturn(expectedPdf);
        when(pdfArtifactStorageService.saveResumo(eq(expectedPdf))).thenReturn(java.nio.file.Path.of("target/reports/resumo.pdf"));

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "3500,00", "1.200.000,00", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("resumo-custos-qiii.pdf"));
        assertArrayEquals(expectedPdf, (byte[]) response.getBody());
        verify(pdfArtifactStorageService, times(1)).saveResumo(eq(expectedPdf));
    }

    @Test
    void shouldGenerateCronogramaFromUploadXlsxAndTexto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        Map<String, Object> qiii = new LinkedHashMap<>();
        Map<String, BigDecimal> table = new LinkedHashMap<>();
        table.put("1. Terreno", new BigDecimal("1200000.00"));

        MatrizCronogramaResponse cronograma = new MatrizCronogramaResponse(
                List.of("2026-01"),
                List.of()
        );

        byte[] expectedResumoPdf = new byte[]{1, 2, 3};
        byte[] expectedCronogramaPdf = new byte[]{4, 5, 6};

        when(qiiiParserService.parseQiii(eq(file), eq(new BigDecimal("3500.00")))).thenReturn(qiii);
        when(costCompositionService.buildCostTable(eq(qiii), eq(new BigDecimal("1200000.00")))).thenReturn(table);
        when(cronogramaFacadeService.gerarAPartirDeTextoEComposicao(eq("texto livre"), eq(table))).thenReturn(cronograma);
        when(pdfReportService.buildCostTablePdf(eq("qiii.xlsx"), eq(new BigDecimal("3500.00")), eq(table))).thenReturn(expectedResumoPdf);
        when(pdfReportService.buildCronogramaPdf(eq("qiii.xlsx"), eq(new BigDecimal("3500.00")), eq(cronograma))).thenReturn(expectedCronogramaPdf);
        when(pdfArtifactStorageService.saveResumo(eq(expectedResumoPdf))).thenReturn(java.nio.file.Path.of("target/reports/resumo.pdf"));
        when(pdfArtifactStorageService.saveCronograma(eq(expectedCronogramaPdf))).thenReturn(java.nio.file.Path.of("target/reports/cronograma.pdf"));

        ResponseEntity<?> response = uploadController.uploadXlsx(
                file,
                "3500,00",
                "1.200.000,00",
                "texto livre"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/zip"), response.getHeaders().getContentType());
        String contentDisposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("relatorios-qiii.zip"));

        byte[] body = (byte[]) response.getBody();
        assertNotNull(body);

        Map<String, byte[]> entries = unzipEntries(body);
        assertEquals(Set.of("resumo-custos-qiii.pdf", "cronograma-fisico-financeiro.pdf"), entries.keySet());
        assertArrayEquals(expectedResumoPdf, entries.get("resumo-custos-qiii.pdf"));
        assertArrayEquals(expectedCronogramaPdf, entries.get("cronograma-fisico-financeiro.pdf"));
        verify(pdfArtifactStorageService, times(1)).saveResumo(eq(expectedResumoPdf));
        verify(pdfArtifactStorageService, times(1)).saveCronograma(eq(expectedCronogramaPdf));
    }

    private Map<String, byte[]> unzipEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();

        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entries.put(entry.getName(), zipInput.readAllBytes());
                zipInput.closeEntry();
            }
        }

        return entries;
    }

    @Test
    void shouldReturnBadRequestWhenFileIsNotXlsx() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.csv",
                "text/csv",
                "dummy".getBytes()
        );

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "3500,00", "1200000,00", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        String error = body.get("error");
        assertNotNull(error);
        assertEquals("Arquivo deve ser .xlsx", error);
    }

    @Test
    void shouldReturnBadRequestWhenDecimalIsInvalid() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "abc", "1200000,00", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        String error = body.get("error");
        assertNotNull(error);
        assertEquals("CUB ou terreno invalidos", error);
    }
}
