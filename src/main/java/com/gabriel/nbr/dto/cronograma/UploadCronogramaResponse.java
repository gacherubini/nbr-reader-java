package com.gabriel.nbr.dto.cronograma;

import java.math.BigDecimal;
import java.util.Map;

public record UploadCronogramaResponse(
        Map<String, BigDecimal> composicaoCustos,
        MatrizCronogramaResponse cronograma
) {
}

