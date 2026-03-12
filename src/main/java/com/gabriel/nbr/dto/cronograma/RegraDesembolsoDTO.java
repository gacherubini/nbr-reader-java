package com.gabriel.nbr.dto.cronograma;

import com.gabriel.nbr.model.cronograma.TipoDistribuicao;

import java.math.BigDecimal;

public record RegraDesembolsoDTO(
        String categoria,
        BigDecimal valorTotal,
        TipoDistribuicao tipo,
        String dataInicio,
        Integer duracaoMeses
) {
}

