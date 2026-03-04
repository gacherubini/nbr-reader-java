package com.gabriel.nbr.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CostCompositionService {

    public Map<String, BigDecimal> buildCostTable(Map<String, Object> qiiiData, BigDecimal terreno) {
        if (terreno == null || terreno.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor de terreno invalido");
        }

        BigDecimal subtotal7 = requiredQiiiValue(qiiiData, "subtotal_1_7");
        BigDecimal subtotal10 = requiredQiiiValue(qiiiData, "subtotal_2_10");
        BigDecimal custo5 = requiredQiiiValue(qiiiData, "custo_básico_global_5");

        BigDecimal projetos = subtotal10.subtract(subtotal7);
        BigDecimal foraObraPadrao = subtotal7.subtract(custo5);

        Map<String, BigDecimal> table = new LinkedHashMap<>();
        table.put("1. Terreno", scale(terreno));
        table.put("2. Projetos (10 - 7)", scale(projetos));
        table.put("3. Obra civil (5)", scale(custo5));
        table.put("4. Fora obra padrao (7 - 5)", scale(foraObraPadrao));

        return table;
    }

    private BigDecimal requiredQiiiValue(Map<String, Object> qiiiData, String key) {
        if (qiiiData == null) throw new IllegalArgumentException("Dados QIII ausentes");

        Object section = qiiiData.get(key);
        if (!(section instanceof Map<?, ?> sectionMap)) {
            throw new IllegalArgumentException("Campo ausente no QIII: " + key);
        }

        Object rawValue = sectionMap.get("value");
        if (rawValue instanceof BigDecimal decimal) {
            return decimal;
        }
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rawValue instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                // keep fallback error below
            }
        }

        throw new IllegalArgumentException("Valor invalido no QIII: " + key);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
