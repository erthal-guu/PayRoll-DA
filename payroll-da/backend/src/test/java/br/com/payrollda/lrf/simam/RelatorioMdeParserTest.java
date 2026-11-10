package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.simam.RelatorioMdeParser.Fundeb;

class RelatorioMdeParserTest {

    /** CSV exportado do SIMAM: RREO MDE do Município de Adrianópolis, 01/2016 a 04/2016. */
    private static String amostra() throws Exception {
        return Files.readString(Path.of(RelatorioMdeParserTest.class.getResource("/simam/exportacao-mde-adrianopolis-2016.csv").toURI()),
                StandardCharsets.UTF_8);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    @Test
    void leOBlocoDoFundebNoCsvExportado() throws Exception {
        assertTrue(RelatorioMdeParser.pareceMde(amostra()));
        Fundeb f = RelatorioMdeParser.deCsv(amostra());
        igual("1094039.06", f.receitasDestinadas());
        igual("875672.79", f.receitasRecebidas());
        igual("853455.00", f.transferencias());
        igual("0", f.complementacaoUniao());
        igual("-240584.06", f.resultadoLiquido());
        igual("743983.69", f.despesasProfissionais());
        igual("1151722.56", f.totalDespesas());
        igual("70.31", f.percentualProfissionais());
        igual("853455.00", f.transferido());
    }

    @Test
    void leOHtmlDaVisualizacaoPeloRotulo() {
        String html = "<table><tr><td>6- RECEITAS DESTINADAS AO FUNDEB</td><td>10,00</td><td>10,00</td><td>9.000,00</td><td>90,00%</td></tr>"
                + "<tr><td>7- RECEITAS RECEBIDAS DO FUNDEB</td><td>20,00</td><td>20,00</td><td>12.345,67</td><td>61,73%</td></tr>"
                + "<tr><td>7.1- FUNDEB - Impostos e Transferências de Impostos</td><td>20,00</td><td>20,00</td><td>12.000,00</td><td>60,00%</td></tr></table>";
        Fundeb f = RelatorioMdeParser.deHtml(html);
        igual("9000.00", f.receitasDestinadas());
        igual("12345.67", f.receitasRecebidas());
        igual("12000.00", f.transferido());
        assertEquals(null, f.resultadoLiquido());
    }

    @Test
    void percentualDoLayoutAtualEOUltimoNumeroDaLinha() {
        String html = "<table><tr><td>7- RECEITAS RECEBIDAS DO FUNDEB</td><td>20,00</td><td>20,00</td><td>6.254.893,18</td><td>99,00%</td></tr>"
                + "<tr><td>Mínimo de 70% do FUNDEB na Remuneração dos Profissionais da Educação Básica</td><td>4.378.425,23</td>"
                + "<td>4.690.000,00</td><td>74,98%</td></tr>"
                + "<tr><td>Mínimo de 70% do FUNDEB na Remuneração (outra linha)</td><td>4.378.425,23</td></tr></table>";
        igual("74.98", RelatorioMdeParser.deHtml(html).percentualProfissionais());
        String soValor = "<table><tr><td>7- RECEITAS RECEBIDAS DO FUNDEB</td><td>1,00</td><td>1,00</td><td>1,00</td></tr>"
                + "<tr><td>Mínimo de 70% do FUNDEB na Remuneração</td><td>4.378.425,23</td></tr></table>";
        assertEquals(null, RelatorioMdeParser.deHtml(soValor).percentualProfissionais());
    }

    @Test
    void recusaOutroRelatorio() {
        assertThrows(RelatorioRgfParser.RelatorioInvalidoException.class,
                () -> RelatorioMdeParser.deCsv("dsItem,valor\nRECEITA CORRENTE LIQUIDA,\" 1.000,00\"\n"));
    }
}
