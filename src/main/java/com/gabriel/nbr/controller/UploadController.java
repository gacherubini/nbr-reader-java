package com.gabriel.nbr.controller;

import com.gabriel.nbr.service.*;
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

    private final com.gabriel.nbr.service.QiiiParserService qiiiParserService;

    public UploadController(com.gabriel.nbr.service.QiiiParserService qiiiParserService) {
        this.qiiiParserService = qiiiParserService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/upload/xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadXlsx(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cub") String cubRaw
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Arquivo não enviado"));
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Arquivo deve ser .xlsx"));
            }

            BigDecimal cub;
            try {
                cub = new BigDecimal(cubRaw.replace(".", "").replace(",", "."));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "CUB inválido"));
            }

            if (cub.compareTo(new BigDecimal("500")) < 0 || cub.compareTo(new BigDecimal("10000")) > 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "CUB fora do intervalo esperado"));
            }

            Map<String, Object> qiiiData = qiiiParserService.parseQiii(file, cub);

            System.out.println(qiiiData);

            return ResponseEntity.ok(qiiiData);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao processar arquivo", "details", e.getMessage()));
        }
    }
}