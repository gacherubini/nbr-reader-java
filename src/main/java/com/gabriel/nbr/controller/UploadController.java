package com.gabriel.nbr.controller;

import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
import com.gabriel.nbr.service.CostCompositionService;
import com.gabriel.nbr.service.PdfArtifactStorageService;
import com.gabriel.nbr.service.PdfReportService;
import com.gabriel.nbr.service.QiiiParserService;
import com.gabriel.nbr.service.cronograma.CronogramaFacadeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping
public class UploadController {

    private static final BigDecimal MIN_CUB = new BigDecimal("500");
    private static final BigDecimal MAX_CUB = new BigDecimal("10000");

    private final QiiiParserService qiiiParserService;
    private final CostCompositionService costCompositionService;
    private final PdfReportService pdfReportService;
    private final PdfArtifactStorageService pdfArtifactStorageService;
    private final CronogramaFacadeService cronogramaFacadeService;

    public UploadController(
            QiiiParserService qiiiParserService,
            CostCompositionService costCompositionService,
            PdfReportService pdfReportService,
            PdfArtifactStorageService pdfArtifactStorageService,
            CronogramaFacadeService cronogramaFacadeService
    ) {
        this.qiiiParserService = qiiiParserService;
        this.costCompositionService = costCompositionService;
        this.pdfReportService = pdfReportService;
        this.pdfArtifactStorageService = pdfArtifactStorageService;
        this.cronogramaFacadeService = cronogramaFacadeService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping(
            value = "/upload/xlsx",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {MediaType.APPLICATION_PDF_VALUE, "application/zip"}
    )
    public ResponseEntity<?> uploadXlsx(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cub") String cubRaw,
            @RequestParam("terreno") String terrenoRaw,
            @RequestParam(value = "texto", required = false) String texto
    ) {
        return buildUploadResponse(file, cubRaw, terrenoRaw, texto);
    }

    private ResponseEntity<?> buildUploadResponse(
            MultipartFile file,
            String cubRaw,
            String terrenoRaw,
            String texto
    ) {
        try {
            ProcessedUpload processedUpload = processUpload(file, cubRaw, terrenoRaw);

            byte[] pdfResumo = pdfReportService.buildCostTablePdf(
                    processedUpload.originalFilename(),
                    processedUpload.cub(),
                    processedUpload.table()
            );
            pdfArtifactStorageService.saveResumo(pdfResumo);

            if (texto != null && !texto.isBlank()) {
                MatrizCronogramaResponse cronograma = cronogramaFacadeService.gerarAPartirDeTextoEComposicao(
                        texto,
                        processedUpload.table()
                );

                byte[] pdfCronograma = pdfReportService.buildCronogramaPdf(
                        processedUpload.originalFilename(),
                        processedUpload.cub(),
                        cronograma
                );
                pdfArtifactStorageService.saveCronograma(pdfCronograma);

                byte[] zip = zipPdfs(pdfResumo, pdfCronograma);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("application/zip"));
                headers.setContentDisposition(ContentDisposition.attachment().filename("relatorios-qiii.zip").build());

                return new ResponseEntity<>(zip, headers, HttpStatus.OK);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("resumo-custos-qiii.pdf").build());

            return new ResponseEntity<>(pdfResumo, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao processar upload", "details", e.getMessage()));
        }
    }

    private byte[] zipPdfs(byte[] pdfResumo, byte[] pdfCronograma) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOutput = new ZipOutputStream(out)) {

            zipOutput.putNextEntry(new ZipEntry("resumo-custos-qiii.pdf"));
            zipOutput.write(pdfResumo);
            zipOutput.closeEntry();

            zipOutput.putNextEntry(new ZipEntry("cronograma-fisico-financeiro.pdf"));
            zipOutput.write(pdfCronograma);
            zipOutput.closeEntry();

            zipOutput.finish();
            return out.toByteArray();
        }
    }

    private ProcessedUpload processUpload(MultipartFile file, String cubRaw, String terrenoRaw) throws IOException {
        ResponseEntity<?> validationError = validateFile(file);
        if (validationError != null) {
            throw new IllegalArgumentException(extractError(validationError));
        }

        BigDecimal cub;
        BigDecimal terreno;
        try {
            cub = parseDecimal(cubRaw);
            terreno = parseDecimal(terrenoRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CUB ou terreno invalidos");
        }

        if (cub.compareTo(MIN_CUB) < 0 || cub.compareTo(MAX_CUB) > 0) {
            throw new IllegalArgumentException("CUB fora do intervalo esperado");
        }
        if (terreno.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Terreno deve ser maior ou igual a zero");
        }

        String originalFilename = file.getOriginalFilename();
        Map<String, Object> qiiiData = qiiiParserService.parseQiii(file, cub);
        Map<String, BigDecimal> table = costCompositionService.buildCostTable(qiiiData, terreno);

        return new ProcessedUpload(originalFilename, cub, table);
    }

    private String extractError(ResponseEntity<?> validationError) {
        Object body = validationError.getBody();
        if (body instanceof Map<?, ?> mapBody && mapBody.get("error") instanceof String message) {
            return message;
        }
        return "Erro de validacao no upload";
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

    private record ProcessedUpload(String originalFilename, BigDecimal cub, Map<String, BigDecimal> table) {
    }
}



