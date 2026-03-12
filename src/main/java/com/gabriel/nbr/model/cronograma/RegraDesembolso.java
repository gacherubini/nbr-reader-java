package com.gabriel.nbr.model.cronograma;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RegraDesembolso(
        String categoria,
        BigDecimal valorTotal,
        TipoDistribuicao tipo,
        YearMonth dataInicio,
        Integer duracaoMeses
) {
}

