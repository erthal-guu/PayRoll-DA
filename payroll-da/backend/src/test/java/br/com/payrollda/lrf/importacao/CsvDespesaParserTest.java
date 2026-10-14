package br.com.payrollda.lrf.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.config.ImportacaoProperties;
import br.com.payrollda.lrf.importacao.LinhaDespesa.Poder;

class CsvDespesaParserTest {

    private final ImportacaoProperties props = new ImportacaoProperties(null, null, List.of(), "UTF-8", ";", 10, 10, Map.ofEntries(
            Map.entry("municipio", "Município"), Map.entry("cod-ibge", "Código IBGE"), Map.entry("entidade", "Entidade"),
            Map.entry("poder", "Poder"), Map.entry("ano", "Ano"), Map.entry("mes", "Mês"),
            Map.entry("despesa-total", "Despesa Total com Pessoal"), Map.entry("rcl-ajustada", "RCL Ajustada"),
            Map.entry("pessoal-ativo", "Pessoal Ativo")));
    private final CsvDespesaParser parser = new CsvDespesaParser(props);

    @Test
    void leLinhasEmFormatoBrasileiroIgnorandoAcentosNoCabecalho() throws Exception {
        String csv = """
                MUNICIPIO;codigo_ibge;ENTIDADE;PODER;ANO;MES;DESPESA TOTAL COM PESSOAL;RCL AJUSTADA;PESSOAL ATIVO
                CURITIBA;4106902;PREFEITURA MUNICIPAL DE CURITIBA;Executivo;2026;2;"5.120.300.000,50";"11.250.000.000,00";4.800.000.000,00
                CURITIBA;4106902;CÂMARA MUNICIPAL DE CURITIBA;Legislativo;2026;2;210.400.000,00;11.250.000.000,00;
                CURITIBA;41069;X;Executivo;2026;2;1,00;2,00;
                """;
        List<LinhaDespesa> ok = new ArrayList<>();
        List<String> erros = new ArrayList<>();

        long lidas = parser.ler(new StringReader(csv), new CsvDespesaParser.Ouvinte() {
            public void linhaValida(LinhaDespesa l) { ok.add(l); }
            public void linhaInvalida(long n, String m) { erros.add(n + ":" + m); }
        });

        assertThat(lidas).isEqualTo(3);
        assertThat(ok).hasSize(2);
        assertThat(ok.get(0).poder()).isEqualTo(Poder.EXECUTIVO);
        assertThat(ok.get(0).despesaTotal()).isEqualByComparingTo("5120300000.50");
        assertThat(ok.get(0).percentual()).isEqualByComparingTo("45.5138");
        assertThat(ok.get(1).poder()).isEqualTo(Poder.LEGISLATIVO);
        assertThat(ok.get(1).pessoalAtivo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(erros).singleElement().asString().startsWith("4:").contains("IBGE");
    }

    @Test
    void recusaArquivoSemColunasObrigatorias() {
        assertThatThrownBy(() -> parser.ler(new StringReader("a;b\n1;2\n"), null))
                .isInstanceOf(CsvDespesaParser.FormatoInvalidoException.class)
                .hasMessageContaining("RCL Ajustada");
    }

    @Test
    void converteNumerosEmVariosFormatos() {
        assertThat(CsvDespesaParser.numeroBr("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(CsvDespesaParser.numeroBr("1234.56")).isEqualByComparingTo("1234.56");
        assertThat(CsvDespesaParser.numeroBr("(10,00)")).isEqualByComparingTo("-10");
    }
}
