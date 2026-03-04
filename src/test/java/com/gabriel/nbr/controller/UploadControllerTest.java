package com.gabriel.nbr.controller;

import com.gabriel.nbr.service.CostCompositionService;
import com.gabriel.nbr.service.PdfReportService;
import com.gabriel.nbr.service.QiiiParserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private QiiiParserService qiiiParserService;

    @Mock
    private CostCompositionService costCompositionService;

    @Mock
    private PdfReportService pdfReportService;

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

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "3500,00", "1.200.000,00");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertNotNull(response.getHeaders().getFirst("Content-Disposition"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("resumo-custos-qiii.pdf"));
        assertArrayEquals(expectedPdf, (byte[]) response.getBody());
    }

    @Test
    void shouldReturnBadRequestWhenFileIsNotXlsx() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.csv",
                "text/csv",
                "dummy".getBytes()
        );

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "3500,00", "1200000,00");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Arquivo deve ser .xlsx", body.get("error"));
    }

    @Test
    void shouldReturnBadRequestWhenDecimalIsInvalid() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiii.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        ResponseEntity<?> response = uploadController.uploadXlsx(file, "abc", "1200000,00");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("CUB ou terreno invalidos", body.get("error"));
    }
}

