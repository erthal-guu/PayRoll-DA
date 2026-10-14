package br.com.payrollda.lrf.importacao;

import java.time.OffsetDateTime;

public record ImportacaoDto(
        long id,
        String origem,
        String referencia,
        String status,
        long linhasLidas,
        long linhasImportadas,
        long linhasComErro,
        String mensagem,
        OffsetDateTime iniciadaEm,
        OffsetDateTime concluidaEm) {

    public record Erro(long linha, String mensagem) {
    }
}
