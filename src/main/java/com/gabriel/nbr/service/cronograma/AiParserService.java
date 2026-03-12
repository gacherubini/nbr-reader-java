package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.dto.cronograma.RegraDesembolsoDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiParserService {

    private final ChatClient chatClient;

    public AiParserService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public List<RegraDesembolsoDTO> extrairRegras(String relatoUsuario) {
        return extrairRegras(relatoUsuario, List.of());
    }

    public List<RegraDesembolsoDTO> extrairRegras(String relatoUsuario, List<String> categoriasObrigatorias) {
        LocalDate hoje = LocalDate.now();

        String contextoCategorias = "";
        if (categoriasObrigatorias != null && !categoriasObrigatorias.isEmpty()) {
            String listaCategorias = categoriasObrigatorias.stream()
                    .map(categoria -> "- " + categoria)
                    .collect(Collectors.joining("\n"));

            contextoCategorias = """

                    Categorias obrigatorias para a resposta:
                    %s

                    Gere exatamente um item por categoria obrigatoria.
                    """.formatted(listaCategorias);
        }

        String systemPrompt = """
                Voce e um extrator de regras de desembolso para um sistema financeiro de incorporacoes.

                Data atual do servidor: %s

                Sua responsabilidade e APENAS converter o relato do usuario em uma lista estruturada de regras.%s

                Regras obrigatorias:
                - Nao faca calculos financeiros.
                - Nao distribua parcelas.
                - Nao invente dados ausentes.
                - Converta datas relativas para formato absoluto YYYY-MM.
                - Tipos validos: A_VISTA, LINEAR, CURVA_S, PERMUTA.
                - valorTotal pode ser informado como 0 quando o valor vier de outra fonte.
                - Responda somente com dados compativeis com a estrutura solicitada.
                - Cada item deve conter: categoria, valorTotal, tipo, dataInicio, duracaoMeses.

                Regras semanticas:
                - A_VISTA: duracaoMeses pode ser 1.
                - PERMUTA: duracaoMeses pode ser 1.
                - LINEAR: duracaoMeses deve ser o numero de meses informado.
                - CURVA_S: duracaoMeses deve ser o numero de meses informado.
                """.formatted(hoje, contextoCategorias);

        return chatClient.prompt()
                .system(system -> system.text(systemPrompt))
                .user(relatoUsuario)
                .call()
                .entity(new ParameterizedTypeReference<>() {});
    }
}
