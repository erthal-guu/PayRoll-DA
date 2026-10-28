package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.importacao.LinhaDespesa;
import br.com.payrollda.lrf.importacao.LinhaDespesa.Poder;

class RelatorioRgfParserTest {

    /** Página "Visualização de Relatório" real do SIMAM (Câmara Municipal de Andirá, 12/2017 a 11/2018). */
    private static String amostra() throws Exception {
        return Files.readString(Path.of(RelatorioRgfParserTest.class.getResource("/simam-rgf-visualizacao.html").toURI()), StandardCharsets.UTF_8);
    }

    @Test
    void leOHtmlDaVisualizacao() throws Exception {
        LinhaDespesa l = RelatorioRgfParser.deHtml(amostra(), "4101507");
        assertEquals("Andirá", l.municipio());
        assertEquals("Câmara Municipal de Andirá", l.entidade());
        assertEquals(Poder.LEGISLATIVO, l.poder());
        assertEquals(2018, l.ano());
        assertEquals(11, l.mes());
        assertEquals(new BigDecimal("1057148.88"), l.despesaBruta());
        assertEquals(new BigDecimal("1057148.88"), l.pessoalAtivo());
        assertEquals(new BigDecimal("1006.01"), l.naoComputadas());
        assertEquals(new BigDecimal("1056142.87"), l.despesaTotal());
        assertEquals(new BigDecimal("59983227.44"), l.rclAjustada());
        assertEquals(0, new BigDecimal("1.7607").compareTo(l.percentual()));
    }

    @Test
    void leOCsvExportadoComAsMesmasLinhas() throws Exception {
        String meses = "Dez/2017,Jan/2018,Fev/2018,Mar/2018,Abr/2018,Mai/2018,Jun/2018,Jul/2018,Ago/2018,Set/2018,Out/2018,Nov/2018,TOTAL,RESTOS";
        String doze = "1,1,1,1,1,1,1,1,1,1,1,1";
        String csv = "﻿\"PREFEITURA MUNICIPAL DE TOLEDO\"\n\"RELATÓRIO DE GESTÃO FISCAL - CONSOLIDADO\"\n\"DEMONSTRATIVO DA DESPESA COM PESSOAL\"\n"
                + "\"DESPESA COM PESSOAL\"," + meses + "\n"
                + "Dez/2017," + meses.substring(9) + "\n"
                + "DESPESA BRUTA COM PESSOAL (I)," + doze + ",\"1.000,00\",0\n"
                + "Pessoal Ativo," + doze + ",\"800,00\",0\n"
                + "Pessoal Inativo e Pensionistas," + doze + ",\"150,00\",0\n"
                + "DESPESAS NÃO COMPUTADAS (II)," + doze + ",\"100,00\",0\n"
                + "\"RECEITA CORRENTE LÍQUIDA AJUSTADA - RCL (VI)\",\"2.000,00\",-\n"
                + "DESPESA TOTAL COM PESSOAL - DTP (IV),\"900,00\",\"45,00%\"\n"
                + "LIMITE MÁXIMO (art.20) - 54%,\"1.080,00\",54%\n";
        LinhaDespesa l = RelatorioRgfParser.deCsv(csv, null);
        assertEquals("Toledo", l.municipio());
        assertEquals(Poder.EXECUTIVO, l.poder());
        assertEquals(new BigDecimal("1000.00"), l.despesaBruta());
        assertEquals(new BigDecimal("900.00"), l.despesaTotal());
    }

    @Test
    void recusaRelatorioDeOutroAnexo() {
        assertThrows(RelatorioRgfParser.RelatorioInvalidoException.class,
                () -> RelatorioRgfParser.deCsv("a,b\nRGF - ANEXO 2,1\n", null));
    }

    @Test
    void leOCsvEmFormatoDeDados() throws Exception {
        String csv = "Textbox78;dsItem1;vlLiquidadoPeriodoMovelAnual;Textbox13\n"
                + "x;DESPESA BRUTA COM PESSOAL (I);\"1.000,00\";y\n"
                + "x;Pessoal Ativo;\"600,00\";y\n"
                + "x;Pessoal Inativo e Pensionistas;\"400,00\";y\n"
                + "x;DESPESAS NÃO COMPUTADAS (II);\"100,00\";y\n"
                + "x;DESPESA TOTAL COM PESSOAL - DTP (III) = (I - II);\"900,00\";y\n"
                + "x;RECEITA CORRENTE LÍQUIDA AJUSTADA (V);\"3.000,00\";y\n";
        String semRcl = csv.substring(0, csv.lastIndexOf("x;RECEITA"));
        assertEquals(true, RelatorioRgfParser.ehCsvDados(csv));
        LinhaDespesa l = RelatorioRgfParser.deCsvDados(csv,
                java.util.Map.of("entidade", "Câmara Municipal de Abatiá", "ano", "2019", "periodo", "Outubro", "municipio", "Abatiá"), "9");
        assertEquals(0, new BigDecimal("1000.00").compareTo(l.despesaBruta()));
        assertEquals(0, new BigDecimal("900.00").compareTo(l.despesaTotal()));
        assertEquals(2019, l.ano());
        assertEquals(10, l.mes());
        LinhaDespesa s = RelatorioRgfParser.deCsvDados(semRcl,
                java.util.Map.of("entidade", "Prefeitura Municipal de Abatiá", "ano", "2015", "periodo", "Novembro"), "9");
        assertEquals(0, BigDecimal.ZERO.compareTo(s.rclAjustada()));
        assertEquals(0, new BigDecimal("900.00").compareTo(s.despesaTotal()));
    }

    @Test
    void leOCsvRealDoSimam() throws Exception {
        String csv = Files.readString(Path.of(RelatorioRgfParserTest.class.getResource("/simam/exportacao-rgf-camara.csv").toURI()), StandardCharsets.UTF_8);
        LinhaDespesa l = RelatorioRgfParser.deCsvDados(csv,
                java.util.Map.of("entidade", "Câmara Municipal de Abatiá", "ano", "2019", "periodo", "Janeiro", "municipio", "Abatiá"), "9");
        assertEquals(0, new BigDecimal("711816.62").compareTo(l.despesaBruta()));
        assertEquals(0, new BigDecimal("711816.62").compareTo(l.pessoalAtivo()));
        assertEquals(0, new BigDecimal("711816.62").compareTo(l.despesaTotal()));
        assertEquals(0, new BigDecimal("19452481.38").compareTo(l.rclAjustada()));
        assertEquals(Poder.LEGISLATIVO, l.poder());
        assertEquals(0, new BigDecimal("3.66").compareTo(l.percentual().setScale(2, java.math.RoundingMode.HALF_UP)));
    }
}
