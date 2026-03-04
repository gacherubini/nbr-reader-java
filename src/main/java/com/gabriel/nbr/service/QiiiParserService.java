package com.gabriel.nbr.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QiiiParserService {

    private static final Pattern MONEY_BR = Pattern.compile("(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final String TARGET_SHEET_NAME = "NBR 12721 Q-III";

    public Map<String, Object> parseQiii(MultipartFile file, BigDecimal cub) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            DataFormatter formatter = new DataFormatter(new Locale("pt", "BR"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("arquivo", file.getOriginalFilename());
            result.put("cub_usuario", cub);
            result.put("subtotal_1_7", null);
            result.put("subtotal_2_10", null);
            result.put("custo_básico_global_5", null);

            Sheet targetSheet = workbook.getSheet(TARGET_SHEET_NAME);
            if (targetSheet == null) {
                return result;
            }

            FoundValue subtotal7 = findValueInSheet(targetSheet, formatter, evaluator, "7.", "subtotal");
            if (subtotal7 != null) result.put("subtotal_1_7", subtotal7.toMap());

            FoundValue subtotal10 = findValueInSheet(targetSheet, formatter, evaluator, "10.", "subtotal");
            if (subtotal10 != null) result.put("subtotal_2_10", subtotal10.toMap());

            FoundValue custo5 = findValueInSheet(targetSheet, formatter, evaluator, "5.", "custo basico global");
            if (custo5 != null) result.put("custo_básico_global_5", custo5.toMap());

            return result;
        }
    }

    private FoundValue findValueInSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            String startsWith,
            String mustContain
    ) {
        for (Row row : sheet) {
            // Scan textual SEM avaliar fórmulas (evita crash do POI)
            String rowText = normalizeText(rowToText(row, formatter));

            if (rowText.startsWith(startsWith) && rowText.contains(mustContain)) {
                BigDecimal value = getRightmostNumber(row, formatter, evaluator);
                if (value != null) {
                    return new FoundValue(
                            sheet.getSheetName(),
                            row.getRowNum() + 1,
                            rowText,
                            value
                    );
                }
            }
        }
        return null;
    }

    /**
     * Concatena o "texto exibido" das células da linha.
     * NÃO usa FormulaEvaluator para não tentar recalcular a planilha.
     */
    private String rowToText(Row row, DataFormatter formatter) {
        if (row == null) return "";

        StringBuilder sb = new StringBuilder();
        short last = row.getLastCellNum();
        if (last < 0) return "";

        for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;

            String v = formatter.formatCellValue(cell); // SEM evaluator
            if (v != null && !v.isBlank()) sb.append(v).append(" | ");
        }
        return sb.toString();
    }

    private BigDecimal getRightmostNumber(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) return null;

        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) return null;

        for (int c = lastCellNum - 1; c >= 0; c--) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;

            BigDecimal parsed = parseDecimalBrFromCell(cell, formatter, evaluator);
            if (parsed != null) return parsed;
        }

        return null;
    }

    /**
     * Regra de ouro:
     * - NÃO use formatter.formatCellValue(cell, evaluator) porque isso força avaliação e pode crashar.
     * - Priorize: NUMERIC -> cached formula result -> texto sem evaluator.
     */
    private BigDecimal parseDecimalBrFromCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;

        // 1) Número direto
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        // 2) Fórmula: tenta pegar o resultado cacheado sem recalcular.
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                CellType cachedType = cell.getCachedFormulaResultType();

                if (cachedType == CellType.NUMERIC) {
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                }

                if (cachedType == CellType.STRING) {
                    String cached = cell.getStringCellValue();
                    BigDecimal parsed = parseDecimalBr(cached);
                    if (parsed != null) return parsed;
                }

                // Se quiser tentar evaluator, faça com try/catch e sem quebrar tudo:
                // (opcional; pode remover se quiser zero risco)
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                    return BigDecimal.valueOf(evaluated.getNumberValue());
                }
            } catch (Exception ignored) {
                // cai pro texto sem evaluator
            }
        }

        // 3) Fallback textual SEM evaluator (não avalia fórmulas)
        String text = formatter.formatCellValue(cell);
        return parseDecimalBr(text);
    }

    private BigDecimal parseDecimalBr(String value) {
        if (value == null) return null;

        String s = value.trim();
        if (s.isEmpty()) return null;

        s = s.replace("R$", "").replace("r$", "").trim();

        Matcher matcher = MONEY_BR.matcher(s);
        if (matcher.find()) {
            s = matcher.group(1);
        } else {
            s = s.replace(" ", "");
        }

        s = s.replace(".", "").replace(",", ".");

        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeText(String s) {
        if (s == null) return "";

        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        normalized = normalized.toLowerCase().trim();
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    private static class FoundValue {
        private final String sheet;
        private final int row;
        private final String label;
        private final BigDecimal value;

        public FoundValue(String sheet, int row, String label, BigDecimal value) {
            this.sheet = sheet;
            this.row = row;
            this.label = label;
            this.value = value;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sheet", sheet);
            map.put("row", row);
            map.put("label", label);
            map.put("value", value);
            return map;
        }
    }
}