package com.gabriel.nbr.controller;

import com.gabriel.nbr.service.CostCompositionService;
import com.gabriel.nbr.service.PdfReportService;
import com.gabriel.nbr.service.QiiiParserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping
public class UploadController {

    private static final BigDecimal MIN_CUB = new BigDecimal("500");
    private static final BigDecimal MAX_CUB = new BigDecimal("10000");

    private final QiiiParserService qiiiParserService;
    private final CostCompositionService costCompositionService;
    private final PdfReportService pdfReportService;

    public UploadController(
            QiiiParserService qiiiParserService,
            CostCompositionService costCompositionService,
            PdfReportService pdfReportService
    ) {
        this.qiiiParserService = qiiiParserService;
        this.costCompositionService = costCompositionService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/upload/xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> uploadXlsx(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cub") String cubRaw,
            @RequestParam("terreno") String terrenoRaw
    ) {
        return buildPdfResponse(file, cubRaw, terrenoRaw);
    }


    private ResponseEntity<?> buildPdfResponse(MultipartFile file, String cubRaw, String terrenoRaw) {
        try {
            ResponseEntity<?> validationError = validateFile(file);
            if (validationError != null) {
                return validationError;
            }

            BigDecimal cub;
            BigDecimal terreno;
            try {
                cub = parseDecimal(cubRaw);
                terreno = parseDecimal(terrenoRaw);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "CUB ou terreno invalidos"));
            }

            if (cub.compareTo(MIN_CUB) < 0 || cub.compareTo(MAX_CUB) > 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "CUB fora do intervalo esperado"));
            }
            if (terreno.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Terreno deve ser maior ou igual a zero"));
            }

            String originalFilename = file.getOriginalFilename();
            Map<String, Object> qiiiData = qiiiParserService.parseQiii(file, cub);
            Map<String, BigDecimal> table = costCompositionService.buildCostTable(qiiiData, terreno);
            byte[] pdf = pdfReportService.buildCostTablePdf(originalFilename, cub, table);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("resumo-custos-qiii.pdf").build());

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao gerar PDF", "details", e.getMessage()));
        }
    }

    private ResponseEntity<?> validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo nao enviado"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo deve ser .xlsx"));
        }

        return null;
    }

    private BigDecimal parseDecimal(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        cleaned = cleaned.replace("R$", "").replace("r$", "").replace(" ", "");

        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }

        return new BigDecimal(cleaned);
    }
}