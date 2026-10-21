package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import br.com.payrollda.lrf.importacao.LinhaDespesa;
import br.com.payrollda.lrf.importacao.LinhaDespesa.Poder;

/**
 * Lê o "RGF - Demonstrativo da Despesa com Pessoal" (Anexo 1) emitido pelo SIMAM, tanto no HTML da tela de
 * visualização quanto no CSV exportado pelo ReportViewer. Os dois são reduzidos a linhas de células e o
 * restante é reconhecido pelo texto do rótulo, sem depender da posição no arquivo.
 */
public final class RelatorioRgfParser {

    /** Falha ao reconhecer o relatório (layout diferente ou outro anexo da LRF). */
    public static class RelatorioInvalidoException extends RuntimeException {
        public RelatorioInvalidoException(String mensagem) {
            super(mensagem);
        }
    }

    private static final Pattern NUMERO = Pattern.compile("-?\\d{1,3}(\\.\\d{3})*(,\\d+)?|-?\\d+(,\\d+)?");
    private static final Pattern MES_ANO = Pattern.compile("(?i)^([a-zç]{3})/(\\d{4})$");
    private static final List<String> MESES = List.of("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez");
    private static final int COLUNA_TOTAL = 12; // 12 meses, depois o total dos últimos 12 meses

    private RelatorioRgfParser() {
    }

    /** Reconhece pelo texto se o conteúdo é o demonstrativo do SIMAM (sem tentar ler tudo). */
    public static boolean pareceRelatorioRgf(String texto) {
        return sem(texto).contains("DEMONSTRATIVO DA DESPESA COM PESSOAL");
    }

    public static LinhaDespesa deHtml(String html, String codIbge) {
        Document doc = Jsoup.parse(html);
        List<List<String>> linhas = new ArrayList<>();
        for (Element tr : doc.select("tr")) {
            if (tr.select("tr").size() > 1) {
                continue; // linha que só embrulha outra tabela
            }
            List<String> celulas = new ArrayList<>();
            for (Element td : tr.children()) {
                if (!td.select("table").isEmpty()) {
                    continue;
                }
                String t = limpar(td.text());
                if (!t.isEmpty()) {
                    celulas.add(t);
                }
            }
            if (!celulas.isEmpty()) {
                linhas.add(celulas);
            }
        }
        List<String> textos = new ArrayList<>();
        for (Element td : doc.select("td")) {
            if (td.select("table").isEmpty() && !limpar(td.text()).isEmpty()) {
                textos.add(limpar(td.text()));
            }
        }
        return ler(linhas, textos, codIbge);
    }

    public static LinhaDespesa deCsv(String csv, String codIbge) throws IOException {
        String texto = csv.startsWith("﻿") ? csv.substring(1) : csv;
        String primeira = texto.lines().findFirst().orElse("");
        char delimitador = primeira.chars().filter(c -> c == ';').count() > primeira.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<List<String>> linhas = new ArrayList<>();
        try (CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(delimitador).setIgnoreEmptyLines(true).build()
                .parse(new StringReader(texto))) {
            for (CSVRecord r : p) {
                List<String> celulas = new ArrayList<>();
                r.forEach(c -> {
                    String t = limpar(c);
                    if (!t.isEmpty()) {
                        celulas.add(t);
                    }
                });
                if (!celulas.isEmpty()) {
                    linhas.add(celulas);
                }
            }
        }
        return ler(linhas, linhas.stream().flatMap(List::stream).toList(), codIbge);
    }

    static LinhaDespesa ler(List<List<String>> linhas, List<String> todas, String codIbge) {
        if (todas.stream().noneMatch(t -> sem(t).contains("DEMONSTRATIVO DA DESPESA COM PESSOAL"))) {
            throw new RelatorioInvalidoException("O relatório recebido não é o Demonstrativo da Despesa com Pessoal (RGF - Anexo 1).");
        }
        String entidade = nomeDaEntidade(todas);
        Matcher m = Pattern.compile("(?i)munic[ií]pal\\s+de\\s+(.+)$").matcher(entidade);
        String municipio = m.find() ? titulo(m.group(1)) : titulo(entidade);

        // último mês do período de 12 meses = última coluna de mês do cabeçalho (ex.: Nov/2018)
        int ano = 0;
        int mes = 0;
        for (List<String> l : linhas) {
            if (l.size() >= 12 && MES_ANO.matcher(l.get(0)).matches()) {
                Matcher mm = MES_ANO.matcher(l.stream().filter(c -> MES_ANO.matcher(c).matches()).reduce((a, b) -> b).orElseThrow());
                mm.matches();
                mes = MESES.indexOf(mm.group(1).toLowerCase(Locale.ROOT)) + 1;
                ano = Integer.parseInt(mm.group(2));
                break;
            }
        }
        if (mes == 0) {
            throw new RelatorioInvalidoException("Não encontrei o período (meses) no relatório.");
        }

        BigDecimal bruta = total(linhas, "DESPESA BRUTA COM PESSOAL");
        BigDecimal ativo = total(linhas, "PESSOAL ATIVO");
        BigDecimal inativos = total(linhas, "PESSOAL INATIVO E PENSIONISTAS");
        BigDecimal outras = totalOuZero(linhas, "OUTRAS DESPESAS DE PESSOAL");
        BigDecimal naoComputadas = total(linhas, "DESPESAS NAO COMPUTADAS");
        BigDecimal rcl = primeiroValor(linhas, "RECEITA CORRENTE LIQUIDA AJUSTADA");
        BigDecimal dtp = primeiroValor(linhas, "DESPESA TOTAL COM PESSOAL");

        List<String> limite = linha(linhas, "LIMITE MAXIMO");
        Poder poder = poderPeloLimite(limite).orElse(sem(entidade).contains("CAMARA") ? Poder.LEGISLATIVO : Poder.EXECUTIVO);

        return new LinhaDespesa(codigo(codIbge, municipio), municipio, titulo(entidade), poder, ano, mes,
                bruta, ativo, inativos, outras, naoComputadas, dtp, rcl);
    }

    /** O nome vem logo antes do título "RELATÓRIO DE GESTÃO FISCAL", na mesma célula (HTML) ou na anterior (CSV). */
    private static String nomeDaEntidade(List<String> textos) {
        for (int i = 0; i < textos.size(); i++) {
            String t = textos.get(i);
            int pos = sem(t).indexOf("RELATORIO DE GESTAO FISCAL");
            if (pos < 0) {
                continue;
            }
            // sem() não muda o tamanho do texto (só remove marcas combinantes depois de decompor)
            String antes = pos > 0 ? limpar(Normalizer.normalize(t, Normalizer.Form.NFC).substring(0, Math.min(pos, t.length()))) : "";
            if (!antes.isEmpty()) {
                return antes;
            }
            if (i > 0) {
                return textos.get(i - 1);
            }
        }
        throw new RelatorioInvalidoException("Não encontrei o nome da entidade no relatório.");
    }

    private static java.util.Optional<Poder> poderPeloLimite(List<String> linha) {
        if (linha == null || linha.size() < 3) {
            return java.util.Optional.empty();
        }
        BigDecimal p = numero(linha.get(linha.size() - 1).replace("%", "").trim());
        for (Poder x : Poder.values()) {
            if (x.limiteMaximo().compareTo(p) == 0) {
                return java.util.Optional.of(x);
            }
        }
        return java.util.Optional.empty();
    }

    private static List<String> linha(List<List<String>> linhas, String prefixo) {
        return linhas.stream().filter(l -> sem(l.get(0)).startsWith(prefixo) || sem(l.get(0)).contains(prefixo)).findFirst().orElse(null);
    }

    private static BigDecimal total(List<List<String>> linhas, String prefixo) {
        BigDecimal v = totalOuNull(linhas, prefixo);
        if (v == null) {
            throw new RelatorioInvalidoException("Linha \"" + prefixo.toLowerCase(Locale.ROOT) + "\" não encontrada no relatório.");
        }
        return v;
    }

    private static BigDecimal totalOuZero(List<List<String>> linhas, String prefixo) {
        BigDecimal v = totalOuNull(linhas, prefixo);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal totalOuNull(List<List<String>> linhas, String prefixo) {
        for (List<String> l : linhas) {
            String rotulo = sem(l.get(0));
            boolean confere = prefixo.equals("PESSOAL ATIVO") || prefixo.equals("PESSOAL INATIVO E PENSIONISTAS")
                    ? rotulo.equals(prefixo) : rotulo.startsWith(prefixo);
            if (confere && l.size() - 1 > COLUNA_TOTAL) {
                return numero(l.get(1 + COLUNA_TOTAL));
            }
        }
        return null;
    }

    /** Valor da linha da apuração do limite (rótulo, valor, % sobre a RCL). */
    private static BigDecimal primeiroValor(List<List<String>> linhas, String prefixo) {
        for (List<String> l : linhas) {
            if (l.size() >= 2 && l.size() <= 3 && sem(l.get(0)).contains(prefixo)) {
                return numero(l.get(1));
            }
        }
        throw new RelatorioInvalidoException("Linha \"" + prefixo.toLowerCase(Locale.ROOT) + "\" não encontrada na apuração do limite.");
    }

    static BigDecimal numero(String t) {
        String s = t.trim();
        if (s.equals("-") || s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (!NUMERO.matcher(s).matches()) {
            throw new RelatorioInvalidoException("Valor numérico inesperado no relatório: \"" + t + "\"");
        }
        return new BigDecimal(s.replace(".", "").replace(',', '.'));
    }

    private static String codigo(String codIbge, String municipio) {
        if (codIbge != null && codIbge.matches("\\d{1,7}")) {
            return codIbge;
        }
        // sem código IBGE no relatório nem no filtro: identificador estável derivado do nome
        return String.format("%07d", Math.abs((long) sem(municipio).hashCode()) % 10_000_000L);
    }

    static String sem(String t) {
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String limpar(String t) {
        return t == null ? "" : t.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    static String titulo(String t) {
        StringBuilder sb = new StringBuilder();
        for (String p : t.toLowerCase(new Locale("pt", "BR")).split(" ")) {
            if (p.isEmpty()) {
                continue;
            }
            boolean minuscula = List.of("de", "da", "do", "das", "dos", "e").contains(p) && sb.length() > 0;
            sb.append(sb.length() > 0 ? " " : "").append(minuscula ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
        }
        return sb.toString();
    }
}
