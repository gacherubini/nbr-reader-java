package com.gabriel.nbr.dto.cronograma;

import java.util.List;

public record MatrizCronogramaResponse(
        List<String> cabecalho,
        List<LinhaCronogramaResponse> linhas
) {
}

