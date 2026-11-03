package br.com.payrollda.lrf.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.dashboard.DashboardDtos.EntidadeLinha;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Pagina;
import br.com.payrollda.lrf.dashboard.DashboardDtos.PontoEvolucao;

class ChatServiceLimiteTest {

    private static final String ERRO_GROQ = "Rate limit reached for model `openai/gpt-oss-120b` in organization `org_x` "
            + "service tier `on_demand` on tokens per minute (TPM): Limit 8000, Used 6900, Requested 1580. "
            + "Please try again in 3.599999999s. Need more tokens?";

    @Test
    void usaCabecalhoRetryAfterQuandoVier() {
        assertThat(ChatService.esperaSugerida("4", ERRO_GROQ)).isEqualTo(4.0);
    }

    @Test
    void leTempoDoTextoDoGroq() {
        assertThat(ChatService.esperaSugerida(null, ERRO_GROQ)).isBetween(3.59, 3.61);
        assertThat(ChatService.esperaSugerida(null, "Please try again in 1m2.5s.")).isEqualTo(62.5);
        assertThat(ChatService.esperaSugerida(null, "Please try again in 870ms.")).isEqualTo(0.87);
        assertThat(ChatService.esperaSugerida(null, "Please try again in 2h3m.")).isEqualTo(7380.0);
        assertThat(ChatService.esperaSugerida(null, "sem tempo")).isEqualTo(-1.0);
    }

    @Test
    void mensagemAmigavelEmPortugues() {
        assertThat(ChatService.mensagemLimite(ERRO_GROQ, 3.6))
                .contains("limite de uso por minuto").contains("4 segundos").doesNotContain("Rate limit");
        assertThat(ChatService.mensagemLimite("Limit 200000 on tokens per day (TPD)", 1500))
                .contains("limite diário").contains("25 minutos");
    }

    @Test
    void ferramentasDevolvemTabelaCompacta() {
        var linha = new EntidadeLinha(7, "4115200", "Maringá", "Câmara Municipal de Maringá", "LEGISLATIVO",
                new BigDecimal("1234567.891"), new BigDecimal("98765432.10"), new BigDecimal("5.4321"),
                new BigDecimal("6.00"), "ALERTA");
        assertThat(ChatService.tabelaEntidades(new Pagina<>(List.of(linha), 1, 0, 8)))
                .isEqualTo("total=1 mostrando=1\nid;municipio;entidade;poder;despesa_rs;rcl_rs;pct_rcl;limite_max_pct;faixa\n"
                        + "7;Maringá;Câmara Municipal de Maringá;LEGISLATIVO;1234568;98765432;5.43;6;ALERTA");
        assertThat(ChatService.tabelaEvolucao(List.of(new PontoEvolucao(2026, 8, new BigDecimal("10.5"),
                new BigDecimal("200"), new BigDecimal("51.3"), new BigDecimal("54.00")))))
                .endsWith("\n2026;8;11;200;51.3;54");
    }
}
