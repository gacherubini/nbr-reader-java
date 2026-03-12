package com.gabriel.nbr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PdfArtifactStorageService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path outputDir;

    public PdfArtifactStorageService(@Value("${app.reports.output-dir:target/reports}") String outputDir) {
        this.outputDir = Path.of(outputDir);
    }

    public Path saveResumo(byte[] pdfBytes) throws IOException {
        return save("resumo-custos-qiii", pdfBytes);
    }

    public Path saveCronograma(byte[] pdfBytes) throws IOException {
        return save("cronograma-fisico-financeiro", pdfBytes);
    }

    private Path save(String prefix, byte[] pdfBytes) throws IOException {
        Files.createDirectories(outputDir);
        String fileName = prefix + "-" + LocalDateTime.now().format(TS_FORMAT) + ".pdf";
        Path target = outputDir.resolve(fileName);
        Files.write(target, pdfBytes, StandardOpenOption.CREATE_NEW);
        return target;
    }
}

