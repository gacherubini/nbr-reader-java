package com.gabriel.nbr.service.cronograma;

import com.gabriel.nbr.dto.cronograma.RegraDesembolsoDTO;
import com.gabriel.nbr.model.cronograma.RegraDesembolso;
import com.gabriel.nbr.model.cronograma.TipoDistribuicao;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class RegraDesembolsoMapper {

    public List<RegraDesembolso> mapear(List<RegraDesembolsoDTO> regrasDto) {
        return regrasDto.stream()
                .map(this::mapear)
                .toList();
    }

    public RegraDesembolso mapear(RegraDesembolsoDTO dto) {
        Integer duracao = resolverDuracao(dto);

        return new RegraDesembolso(
                dto.categoria().trim(),
                dto.valorTotal(),
                dto.tipo(),
                YearMonth.parse(dto.dataInicio()),
                duracao
        );
    }

    private Integer resolverDuracao(RegraDesembolsoDTO dto) {
        if (dto.tipo() == TipoDistribuicao.A_VISTA || dto.tipo() == TipoDistribuicao.PERMUTA) {
            return 1;
        }
        return dto.duracaoMeses();
    }
}

