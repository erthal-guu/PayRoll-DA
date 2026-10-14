package br.com.payrollda.lrf.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Respostas da API do dashboard. O front (src/api) usa exatamente estes formatos. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Periodo(int ano, int mes) {
    }

    public record Municipio(String codIbge, String municipio) {
    }

    public record Composicao(BigDecimal pessoalAtivo, BigDecimal inativosPensionistas,
                             BigDecimal outrasDespesas, BigDecimal naoComputadas) {
    }

    public record Resumo(Periodo periodo, long entidades, BigDecimal despesaTotal, BigDecimal rclTotal,
                         BigDecimal usoMedioLimite, Map<String, Long> faixas, Composicao composicao) {
    }

    public record EntidadeLinha(long entidadeId, String codIbge, String municipio, String entidade, String poder,
                                BigDecimal despesaTotal, BigDecimal rclAjustada, BigDecimal percentual,
                                BigDecimal limiteMaximo, String faixa) {
    }

    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {
    }

    public record PontoEvolucao(int ano, int mes, BigDecimal despesaTotal, BigDecimal rclAjustada,
                                BigDecimal percentual, BigDecimal limiteMaximo) {
    }

    public record EntidadeResumo(long id, String nome, String poder) {
    }

    /** Linhas do Demonstrativo da Despesa com Pessoal (RGF) de uma entidade num período. */
    public record Demonstrativo(long entidadeId, String codIbge, String municipio, String entidade, String poder,
                                int ano, int mes, BigDecimal despesaBruta, BigDecimal pessoalAtivo,
                                BigDecimal inativosPensionistas, BigDecimal outrasDespesas, BigDecimal naoComputadas,
                                BigDecimal despesaTotal, BigDecimal rclAjustada, BigDecimal percentual,
                                BigDecimal limiteMaximo, String faixa) {
    }

    public record FaixasNoPeriodo(int ano, int mes, Map<String, Long> faixas) {
    }
}
