package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import br.com.payrollda.lrf.simam.RelatorioRgfParser.RelatorioInvalidoException;

/**
 * Lê o bloco do FUNDEB do "RREO - Demonstrativo das Receitas e Despesas com Manutenção e Desenvolvimento do Ensino
 * (MDE)" emitido pelo SIMAM, no CSV exportado pelo ReportViewer ou no HTML da visualização. Os itens são achados pelo
 * texto do rótulo, sem depender da numeração, que muda entre as versões do demonstrativo.
 */
public final class RelatorioMdeParser {

    /** Valores realizados até o período do relatório (no último bimestre, o ano inteiro). Nulo quando o item não veio. */
    public record Fundeb(BigDecimal receitasDestinadas, BigDecimal receitasRecebidas, BigDecimal transferencias,
                         BigDecimal complementacaoUniao, BigDecimal resultadoLiquido, BigDecimal despesasProfissionais,
                         BigDecimal totalDespesas, BigDecimal percentualProfissionais) {

        /** O valor das "Transferências do FUNDEB" do relatório de análise: o repasse do fundo ao município. */
        public BigDecimal transferido() {
            return transferencias != null ? transferencias : receitasRecebidas;
        }
    }

    private RelatorioMdeParser() {
    }

    /** Reconhece pelo texto se o conteúdo é o demonstrativo MDE com o bloco do FUNDEB. */
    public static boolean pareceMde(String texto) {
        String t = RelatorioRgfParser.sem(texto == null ? "" : texto);
        return t.contains("FUNDEB") && (t.contains("DESENVOLVIMENTO DO ENSINO") || t.contains("RECEITAS RECEBIDAS DO FUNDEB"));
    }

    public static Fundeb deCsv(String csv) throws IOException {
        List<List<String>> linhas = new ArrayList<>();
        try (CSVParser p = CSVFormat.DEFAULT.parse(new StringReader(csv.replace("﻿", "")))) {
            for (CSVRecord r : p) {
                List<String> c = new ArrayList<>();
                r.forEach(x -> c.add(x == null ? "" : x.trim()));
                linhas.add(c);
            }
        }
        return ler(linhas);
    }

    public static Fundeb deHtml(String html) {
        List<List<String>> linhas = new ArrayList<>();
        for (Element tr : Jsoup.parse(html).select("tr")) {
            if (tr.select("tr").size() > 1) {
                continue; // linha que só embrulha outra tabela
            }
            List<String> c = new ArrayList<>();
            for (Element td : tr.children()) {
                String t = td.text().replace(' ', ' ').replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) {
                    c.add(t);
                }
            }
            linhas.add(c);
        }
        return ler(linhas);
    }

    /** Item do demonstrativo: o rótulo e os números que vêm depois dele na mesma linha. */
    record Item(String rotulo, List<BigDecimal> valores) {
    }

    static Fundeb ler(List<List<String>> linhas) {
        List<Item> itens = new ArrayList<>();
        for (List<String> l : linhas) {
            String rotulo = null;
            List<BigDecimal> valores = new ArrayList<>();
            for (String c : l) {
                if (rotulo == null) {
                    // o CSV repete às vezes a fórmula da coluna antes do rótulo, ex.: "%(h) =(g/d)x100"
                    if (c.matches("(?s).*\\p{L}{3,}.*") && !c.startsWith("%")) {
                        rotulo = RelatorioRgfParser.sem(c);
                    }
                    continue;
                }
                BigDecimal v = numero(c);
                if (v != null) {
                    valores.add(v);
                }
            }
            if (rotulo != null && !valores.isEmpty()) {
                itens.add(new Item(rotulo, valores));
            }
        }
        BigDecimal destinadas = realizado(itens, "RECEITAS DESTINADAS AO FUNDEB");
        BigDecimal recebidas = realizado(itens, "RECEITAS RECEBIDAS DO FUNDEB");
        if (destinadas == null && recebidas == null) {
            throw new RelatorioInvalidoException("Não encontrei o bloco do FUNDEB (receitas destinadas e recebidas) no demonstrativo MDE.");
        }
        BigDecimal transferencias = realizado(itens, "TRANSFERENCIAS DE RECURSOS DO FUNDEB", "FUNDEB - IMPOSTOS E TRANSFERENCIAS DE IMPOSTOS");
        BigDecimal complementacao = realizado(itens, "COMPLEMENTACAO DA UNIAO AO FUNDEB", "FUNDEB - COMPLEMENTACAO DA UNIAO");
        BigDecimal resultado = realizado(itens, "RESULTADO LIQUIDO DAS TRANSFERENCIAS DO FUNDEB");
        BigDecimal profissionais = liquidado(itens, "PAGAMENTO DOS PROFISSIONAIS DO MAGISTERIO", "PROFISSIONAIS DA EDUCACAO BASICA");
        BigDecimal total = liquidado(itens, "TOTAL DAS DESPESAS DO FUNDEB", "TOTAL DAS DESPESAS COM RECURSOS DO FUNDEB");
        BigDecimal percentual = percentual(itens, "MINIMO DE 60% DO FUNDEB", "MINIMO DE 70% DO FUNDEB");
        return new Fundeb(destinadas, recebidas, transferencias, complementacao, resultado, profissionais, total, percentual);
    }

    private static Item achar(List<Item> itens, String... chaves) {
        for (String chave : chaves) {
            for (Item it : itens) {
                if (it.rotulo().contains(chave)) {
                    return it;
                }
            }
        }
        return null;
    }

    /** Receitas: previsão inicial, previsão atualizada, realizado até o bimestre, %. */
    private static BigDecimal realizado(List<Item> itens, String... chaves) {
        Item it = achar(itens, chaves);
        if (it == null) {
            return null;
        }
        return it.valores().size() >= 3 ? it.valores().get(2) : it.valores().get(it.valores().size() - 1);
    }

    /** Despesas: dotação inicial, dotação atualizada, empenhado, %, liquidado, %. Fica com o liquidado. */
    private static BigDecimal liquidado(List<Item> itens, String... chaves) {
        Item it = achar(itens, chaves);
        if (it == null) {
            return null;
        }
        List<BigDecimal> v = it.valores();
        return v.size() >= 5 ? v.get(4) : v.size() >= 3 ? v.get(2) : v.get(v.size() - 1);
    }

    /**
     * Indicador do mínimo aplicado nos profissionais. No layout antigo a linha traz só o percentual; no atual traz valor
     * exigido, valor aplicado e % aplicado, então fica o último número. Um valor fora de 0 a 1000 não é percentual.
     */
    private static BigDecimal percentual(List<Item> itens, String... chaves) {
        Item it = achar(itens, chaves);
        if (it == null) {
            return null;
        }
        BigDecimal v = it.valores().get(it.valores().size() - 1);
        return v.abs().compareTo(new BigDecimal("1000")) < 0 ? v : null;
    }

    /** Número no formato do relatório ("1.234,56", "- 240.584,06", "10,82%"); nulo para texto. */
    static BigDecimal numero(String t) {
        String s = t.replace("R$", "").replace("%", "").replace(" ", "").replace(' ', ' ').trim();
        if (!s.matches("-?\\d{1,3}(\\.\\d{3})*(,\\d+)?|-?\\d+(,\\d+)?")) {
            return null;
        }
        return new BigDecimal(s.replace(".", "").replace(",", "."));
    }
}
