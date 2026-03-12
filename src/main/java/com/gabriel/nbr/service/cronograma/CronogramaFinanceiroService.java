package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.model.cronograma.RegraDesembolso;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Service
public class CronogramaFinanceiroService {

    public TreeMap<YearMonth, BigDecimal> calcular(RegraDesembolso regra) {
        TreeMap<YearMonth, BigDecimal> cronograma = new TreeMap<>();

        return switch (regra.tipo()) {
            case PERMUTA -> distribuirPermuta(cronograma, regra);
            case A_VISTA -> distribuirAVista(cronograma, regra);
            case LINEAR -> distribuirLinear(cronograma, regra);
            case CURVA_S -> distribuirCurvaS(cronograma, regra);
        };
    }

    private TreeMap<YearMonth, BigDecimal> distribuirPermuta(
            TreeMap<YearMonth, BigDecimal> cronograma,
            RegraDesembolso regra
    ) {
        cronograma.put(regra.dataInicio(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return cronograma;
    }

    private TreeMap<YearMonth, BigDecimal> distribuirAVista(
            TreeMap<YearMonth, BigDecimal> cronograma,
            RegraDesembolso regra
    ) {
        cronograma.put(regra.dataInicio(), money(regra.valorTotal()));
        return cronograma;
    }

    private TreeMap<YearMonth, BigDecimal> distribuirLinear(
            TreeMap<YearMonth, BigDecimal> cronograma,
            RegraDesembolso regra
    ) {
        int meses = regra.duracaoMeses();
        BigDecimal total = money(regra.valorTotal());

        BigDecimal parcelaBase = total.divide(BigDecimal.valueOf(meses), 2, RoundingMode.DOWN);
        BigDecimal acumulado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (int i = 0; i < meses; i++) {
            YearMonth competencia = regra.dataInicio().plusMonths(i);

            BigDecimal valorParcela;
            if (i < meses - 1) {
                valorParcela = parcelaBase;
                acumulado = acumulado.add(valorParcela);
            } else {
                valorParcela = total.subtract(acumulado).setScale(2, RoundingMode.HALF_UP);
            }

            cronograma.put(competencia, valorParcela);
        }

        return cronograma;
    }

    private TreeMap<YearMonth, BigDecimal> distribuirCurvaS(
            TreeMap<YearMonth, BigDecimal> cronograma,
            RegraDesembolso regra
    ) {
        int meses = regra.duracaoMeses();
        BigDecimal total = money(regra.valorTotal());

        List<BigDecimal> pesos = gerarPesosCurvaS(meses);
        BigDecimal somaPesos = pesos.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal acumulado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (int i = 0; i < meses; i++) {
            YearMonth competencia = regra.dataInicio().plusMonths(i);

            BigDecimal valorParcela;
            if (i < meses - 1) {
                valorParcela = total
                        .multiply(pesos.get(i))
                        .divide(somaPesos, 2, RoundingMode.DOWN);

                acumulado = acumulado.add(valorParcela);
            } else {
                valorParcela = total.subtract(acumulado).setScale(2, RoundingMode.HALF_UP);
            }

            cronograma.put(competencia, valorParcela);
        }

        return cronograma;
    }

    private List<BigDecimal> gerarPesosCurvaS(int meses) {
        List<BigDecimal> pesos = new ArrayList<>();

        for (int i = 0; i < meses; i++) {
            int peso = Math.min(i + 1, meses - i);
            pesos.add(BigDecimal.valueOf(peso));
        }

        return pesos;
    }

    private BigDecimal money(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}

