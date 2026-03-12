package com.gabriel.nbr.dto.cronograma;

import java.math.BigDecimal;
import java.util.List;

public record LinhaCronogramaResponse(
        String categoria,
        List<BigDecimal> valores
) {
}

