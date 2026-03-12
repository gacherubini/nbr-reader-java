package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.dto.cronograma.LinhaCronogramaResponse;
import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class MatrizCronogramaBuilder {

    public MatrizCronogramaResponse build(Map<String, TreeMap<YearMonth, BigDecimal>> cronogramasPorCategoria) {
        if (cronogramasPorCategoria == null || cronogramasPorCategoria.isEmpty()) {
            return new MatrizCronogramaResponse(List.of(), List.of());
        }

        YearMonth marcoZero = encontrarMenorMes(cronogramasPorCategoria);
        YearMonth mesFinal = encontrarMaiorMes(cronogramasPorCategoria);

        List<YearMonth> eixoMeses = gerarEixoMeses(marcoZero, mesFinal);
        List<String> cabecalho = eixoMeses.stream()
                .map(YearMonth::toString)
                .toList();

        List<LinhaCronogramaResponse> linhas = new ArrayList<>();

        for (Map.Entry<String, TreeMap<YearMonth, BigDecimal>> entry : cronogramasPorCategoria.entrySet()) {
            String categoria = entry.getKey();
            TreeMap<YearMonth, BigDecimal> cronograma = entry.getValue();

            List<BigDecimal> valores = new ArrayList<>();
            for (YearMonth mes : eixoMeses) {
                valores.add(cronograma.getOrDefault(
                        mes,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                ));
            }

            linhas.add(new LinhaCronogramaResponse(categoria, valores));
        }

        return new MatrizCronogramaResponse(cabecalho, linhas);
    }

    private YearMonth encontrarMenorMes(Map<String, TreeMap<YearMonth, BigDecimal>> cronogramasPorCategoria) {
        return cronogramasPorCategoria.values().stream()
                .filter(mapa -> !mapa.isEmpty())
                .map(TreeMap::firstKey)
                .min(YearMonth::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("Não foi possível encontrar o marco zero."));
    }

    private YearMonth encontrarMaiorMes(Map<String, TreeMap<YearMonth, BigDecimal>> cronogramasPorCategoria) {
        return cronogramasPorCategoria.values().stream()
                .filter(mapa -> !mapa.isEmpty())
                .map(TreeMap::lastKey)
                .max(YearMonth::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("Não foi possível encontrar o mês final."));
    }

    private List<YearMonth> gerarEixoMeses(YearMonth inicio, YearMonth fim) {
        List<YearMonth> meses = new ArrayList<>();
        YearMonth cursor = inicio;

        while (!cursor.isAfter(fim)) {
            meses.add(cursor);
            cursor = cursor.plusMonths(1);
        }

        return meses;
    }
}

