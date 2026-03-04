package com.gabriel.nbr.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CostCompositionServiceTest {

    private final CostCompositionService service = new CostCompositionService();

    @Test
    void shouldBuildCostTableFromQiiiData() {
        Map<String, Object> qiii = new LinkedHashMap<>();
        qiii.put("subtotal_1_7", section("28051740.64"));
        qiii.put("subtotal_2_10", section("28331740.64"));
        qiii.put("custo_básico_global_5", section("26644743.17"));

        Map<String, BigDecimal> table = service.buildCostTable(qiii, new BigDecimal("1000000.00"));

        assertEquals(new BigDecimal("1000000.00"), table.get("1. Terreno"));
        assertEquals(new BigDecimal("280000.00"), table.get("2. Projetos (10 - 7)"));
        assertEquals(new BigDecimal("26644743.17"), table.get("3. Obra civil (5)"));
        assertEquals(new BigDecimal("1406997.47"), table.get("4. Fora obra padrao (7 - 5)"));
    }

    @Test
    void shouldFailWhenAnySectionIsMissing() {
        Map<String, Object> qiii = new LinkedHashMap<>();
        qiii.put("subtotal_1_7", section("10"));

        assertThrows(IllegalArgumentException.class,
                () -> service.buildCostTable(qiii, BigDecimal.ONE));
    }

    private Map<String, Object> section(String value) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("value", new BigDecimal(value));
        return section;
    }
}

