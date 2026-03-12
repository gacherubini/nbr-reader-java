package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.dto.cronograma.MatrizCronogramaResponse;
import com.gabriel.nbr.dto.cronograma.RegraDesembolsoDTO;
import com.gabriel.nbr.model.cronograma.RegraDesembolso;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class CronogramaFacadeService {

    private static final Pattern PREFIXO_NUMERICO = Pattern.compile("^\\d+\\.\\s*");
    private static final Pattern SUFIXO_PARENTESES = Pattern.compile("\\s*\\(.*\\)$");

    private final AiParserService aiParserService;
    private final RegraDesembolsoValidator validator;
    private final RegraDesembolsoMapper mapper;
    private final CronogramaFinanceiroService cronogramaFinanceiroService;
    private final MatrizCronogramaBuilder matrizCronogramaBuilder;

    public CronogramaFacadeService(
            AiParserService aiParserService,
            RegraDesembolsoValidator validator,
            RegraDesembolsoMapper mapper,
            CronogramaFinanceiroService cronogramaFinanceiroService,
            MatrizCronogramaBuilder matrizCronogramaBuilder
    ) {
        this.aiParserService = aiParserService;
        this.validator = validator;
        this.mapper = mapper;
        this.cronogramaFinanceiroService = cronogramaFinanceiroService;
        this.matrizCronogramaBuilder = matrizCronogramaBuilder;
    }

    public MatrizCronogramaResponse gerarAPartirDeTexto(String texto) {
        List<RegraDesembolsoDTO> regrasDto = aiParserService.extrairRegras(texto);
        return gerarAPartirDeRegras(regrasDto);
    }

    public MatrizCronogramaResponse gerarAPartirDeTextoEComposicao(String texto, Map<String, BigDecimal> composicaoCustos) {
        if (composicaoCustos == null || composicaoCustos.isEmpty()) {
            throw new IllegalArgumentException("Composicao de custos ausente para gerar cronograma.");
        }

        List<String> categoriasBase = composicaoCustos.keySet().stream()
                .map(this::limparCategoriaTabela)
                .toList();


        // extrair llm
        List<RegraDesembolsoDTO> regrasInferidas = aiParserService.extrairRegras(texto, categoriasBase);


        List<RegraDesembolsoDTO> regrasComValorDoXlsx = composicaoCustos.entrySet().stream()
                .map(entry -> montarRegraComValorDoXlsx(entry, regrasInferidas))
                .toList();

        return gerarAPartirDeRegras(regrasComValorDoXlsx);
    }

    public MatrizCronogramaResponse gerarAPartirDeRegras(List<RegraDesembolsoDTO> regrasDto) {
        validator.validar(regrasDto);

        List<RegraDesembolso> regras = mapper.mapear(regrasDto);

        Map<String, TreeMap<YearMonth, BigDecimal>> cronogramasPorCategoria = new LinkedHashMap<>();

        for (RegraDesembolso regra : regras) {
            TreeMap<YearMonth, BigDecimal> cronogramaRegra = cronogramaFinanceiroService.calcular(regra);

            cronogramasPorCategoria.computeIfAbsent(regra.categoria(), key -> new TreeMap<>());

            TreeMap<YearMonth, BigDecimal> acumuladoCategoria = cronogramasPorCategoria.get(regra.categoria());

            for (Map.Entry<YearMonth, BigDecimal> entry : cronogramaRegra.entrySet()) {
                acumuladoCategoria.merge(
                        entry.getKey(),
                        entry.getValue(),
                        (valorAtual, novoValor) -> valorAtual.add(novoValor).setScale(2, RoundingMode.HALF_UP)
                );
            }
        }

        return matrizCronogramaBuilder.build(cronogramasPorCategoria);
    }

    private RegraDesembolsoDTO montarRegraComValorDoXlsx(
            Map.Entry<String, BigDecimal> custo,
            List<RegraDesembolsoDTO> regrasInferidas
    ) {
        String categoriaBase = limparCategoriaTabela(custo.getKey());
        RegraDesembolsoDTO regraInferida = encontrarRegraPorCategoria(categoriaBase, regrasInferidas);

        return new RegraDesembolsoDTO(
                categoriaBase,
                custo.getValue(),
                regraInferida.tipo(),
                regraInferida.dataInicio(),
                regraInferida.duracaoMeses()
        );
    }

    private RegraDesembolsoDTO encontrarRegraPorCategoria(String categoriaBase, List<RegraDesembolsoDTO> regras) {
        String categoriaNormalizada = normalizar(categoriaBase);

        return regras.stream()
                .filter(regra -> regra != null && regra.categoria() != null)
                .filter(regra -> {
                    String regraNormalizada = normalizar(regra.categoria());
                    return regraNormalizada.equals(categoriaNormalizada)
                            || regraNormalizada.contains(categoriaNormalizada)
                            || categoriaNormalizada.contains(regraNormalizada);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "A IA nao retornou regra para a categoria: " + categoriaBase
                ));
    }

    private String limparCategoriaTabela(String categoriaTabela) {
        String semPrefixo = PREFIXO_NUMERICO.matcher(categoriaTabela).replaceFirst("");
        return SUFIXO_PARENTESES.matcher(semPrefixo).replaceFirst("").trim();
    }

    private String normalizar(String valor) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcento.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }
}
