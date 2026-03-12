package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.dto.cronograma.RegraDesembolsoDTO;
import com.gabriel.nbr.model.cronograma.TipoDistribuicao;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class RegraDesembolsoValidator {

    public void validar(List<RegraDesembolsoDTO> regras) {
        if (regras == null || regras.isEmpty()) {
            throw new IllegalArgumentException("A IA não retornou regras de desembolso.");
        }

        for (RegraDesembolsoDTO regra : regras) {
            validarRegra(regra);
        }
    }

    private void validarRegra(RegraDesembolsoDTO regra) {
        if (regra == null) {
            throw new IllegalArgumentException("Existe uma regra nula na resposta da IA.");
        }

        if (regra.categoria() == null || regra.categoria().isBlank()) {
            throw new IllegalArgumentException("Categoria é obrigatória.");
        }

        if (regra.valorTotal() == null) {
            throw new IllegalArgumentException("valorTotal é obrigatório.");
        }

        if (regra.valorTotal().signum() < 0) {
            throw new IllegalArgumentException("valorTotal não pode ser negativo.");
        }

        if (regra.tipo() == null) {
            throw new IllegalArgumentException("tipo é obrigatório.");
        }

        if (regra.dataInicio() == null || regra.dataInicio().isBlank()) {
            throw new IllegalArgumentException("dataInicio é obrigatória.");
        }

        validarFormatoYearMonth(regra.dataInicio());

        if (exigeDuracao(regra.tipo())) {
            if (regra.duracaoMeses() == null || regra.duracaoMeses() <= 0) {
                throw new IllegalArgumentException(
                        "duracaoMeses deve ser maior que zero para o tipo " + regra.tipo()
                );
            }
        }
    }

    private boolean exigeDuracao(TipoDistribuicao tipo) {
        return tipo == TipoDistribuicao.LINEAR || tipo == TipoDistribuicao.CURVA_S;
    }

    private void validarFormatoYearMonth(String valor) {
        try {
            YearMonth.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "dataInicio deve estar no formato YYYY-MM. Valor recebido: " + valor
            );
        }
    }
}

