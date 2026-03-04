package com.gabriel.nbr.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QiiiParserServiceTest {

    private static final String TARGET_SHEET_NAME = "NBR 12721 Q-III";
    private final QiiiParserService service = new QiiiParserService();

    @Test
    void shouldReadOnlyTargetSheet() throws IOException {
        MockMultipartFile file = workbookWithTargetAndOtherSheet();

        Map<String, Object> result = service.parseQiii(file, new BigDecimal("1234.56"));

        assertValueFromSheet(result, "subtotal_1_7", new BigDecimal("111.11"));
        assertValueFromSheet(result, "subtotal_2_10", new BigDecimal("222.22"));
        assertValueFromSheet(result, "custo_básico_global_5", new BigDecimal("333.33"));
    }

    @Test
    void shouldReturnNullValuesWhenTargetSheetDoesNotExist() throws IOException {
        MockMultipartFile file = workbookOnlyWithOtherSheet();

        Map<String, Object> result = service.parseQiii(file, BigDecimal.ONE);

        assertNull(result.get("subtotal_1_7"));
        assertNull(result.get("subtotal_2_10"));
        assertNull(result.get("custo_básico_global_5"));
    }

    private void assertValueFromSheet(Map<String, Object> result, String key, BigDecimal expectedValue) {
        Object raw = result.get(key);
        assertNotNull(raw, "Esperava valor em " + key);

        @SuppressWarnings("unchecked")
        Map<String, Object> found = (Map<String, Object>) raw;

        assertEquals(TARGET_SHEET_NAME, found.get("sheet"));
        BigDecimal actual = (BigDecimal) found.get("value");
        assertEquals(0, actual.compareTo(expectedValue));
    }

    private MockMultipartFile workbookWithTargetAndOtherSheet() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet other = workbook.createSheet("Outra Aba");
            addRow(other, 0, "7. subtotal", 999.99);
            addRow(other, 1, "10. subtotal", 999.99);
            addRow(other, 2, "5. custo basico global", 999.99);

            Sheet target = workbook.createSheet(TARGET_SHEET_NAME);
            addRow(target, 0, "7. subtotal", 111.11);
            addRow(target, 1, "10. subtotal", 222.22);
            addRow(target, 2, "5. custo basico global", 333.33);

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "qiii.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile workbookOnlyWithOtherSheet() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet other = workbook.createSheet("Outra Aba");
            addRow(other, 0, "7. subtotal", 999.99);
            addRow(other, 1, "10. subtotal", 999.99);
            addRow(other, 2, "5. custo basico global", 999.99);

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "qiii.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private void addRow(Sheet sheet, int rowNum, String label, double value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
