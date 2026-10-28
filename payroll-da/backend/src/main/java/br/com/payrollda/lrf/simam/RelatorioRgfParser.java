package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        String t = sem(texto);
        return t.contains("DESPESA COM PESSOAL") && (t.contains("DEMONSTRATIVO") || t.contains("RELATORIO DE GESTAO FISCAL"));
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

    /** O CSV do ReportViewer do SIMAM vem em formato de dados: cabeçalho com nomes dos controles (dsItem1, vl...). */
    public static boolean ehCsvDados(String csv) {
        return csv != null && csv.contains("dsItem1") && csv.contains("vlLiquidadoPeriodoMovelAnual");
    }

    /**
     * Lê o CSV em formato de dados: cada linha tem o nome do item (dsItem1) e o valor dos últimos 12 meses
     * (vlLiquidadoPeriodoMovelAnual). Entidade, município, ano e período não vêm no CSV; saem dos rótulos escolhidos
     * nos filtros do formulário.
     */
    public static LinhaDespesa deCsvDados(String csv, Map<String, String> rotulos, String codIbge) throws IOException {
        String texto = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        String primeira = texto.lines().findFirst().orElse("");
        char delimitador = primeira.chars().filter(c -> c == ';').count() > primeira.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<String[]> itens = new ArrayList<>(); // {rótulo sem acento, rótulo original, valor}
        int iItem = -1;
        int iValor = -1;
        try (CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(delimitador).setIgnoreEmptyLines(true).build()
                .parse(new StringReader(texto))) {
            for (CSVRecord r : p) {
                if (iItem < 0) {
                    for (int i = 0; i < r.size(); i++) {
                        if (limpar(r.get(i)).equals("dsItem1")) {
                            iItem = i;
                        } else if (limpar(r.get(i)).equals("vlLiquidadoPeriodoMovelAnual")) {
                            iValor = i;
                        }
                    }
                    if (iItem < 0 || iValor < 0) {
                        iItem = -1;
                        iValor = -1;
                    }
                    continue;
                }
                if (r.size() > Math.max(iItem, iValor)) {
                    String rotulo = limpar(r.get(iItem));
                    String valor = limpar(r.get(iValor));
                    if (!rotulo.isEmpty() && !valor.isEmpty()) {
                        itens.add(new String[] {sem(rotulo), rotulo, valor});
                    }
                }
            }
        }
        if (itens.isEmpty()) {
            throw new RelatorioInvalidoException("O CSV do SIMAM não trouxe linhas com os itens do demonstrativo.");
        }
        BigDecimal bruta = item(itens, true, "DESPESA BRUTA COM PESSOAL");
        BigDecimal ativo = itemIgual(itens, "PESSOAL ATIVO");
        BigDecimal inativos = itemIgual(itens, "PESSOAL INATIVO E PENSIONISTAS");
        BigDecimal outras = itemOuZero(itens, "OUTRAS DESPESAS DE PESSOAL");
        BigDecimal naoComputadas = item(itens, true, "DESPESAS NAO COMPUTADAS");
        BigDecimal dtp = item(itens, true, "DESPESA TOTAL COM PESSOAL");
        // O CSV do SIMAM traz só os itens de despesa; a RCL fica em outro relatório. Sem ela, grava 0 e o % fica em 0.
        BigDecimal rcl = itemOuZero(itens, "RECEITA CORRENTE LIQUIDA");

        String entidade = rotulo(rotulos, "entidade");
        if (entidade.isEmpty()) {
            throw new RelatorioInvalidoException("Não sei qual é a entidade: o formulário do SIMAM não informou o campo Entidade.");
        }
        int ano = inteiro(rotulo(rotulos, "ano"));
        int mes = mesDoPeriodo(rotulo(rotulos, "periodo"));
        if (ano == 0 || mes == 0) {
            throw new RelatorioInvalidoException("Não sei o ano e o período do relatório (ano=\"" + rotulo(rotulos, "ano")
                    + "\", período=\"" + rotulo(rotulos, "periodo") + "\").");
        }
        String municipio = titulo(rotulo(rotulos, "municipio"));
        if (municipio.isEmpty()) {
            Matcher m = Pattern.compile("(?i)munic[ií]pal\\s+de\\s+(.+)$").matcher(entidade);
            municipio = m.find() ? titulo(m.group(1)) : titulo(entidade);
        }
        Poder poder = sem(entidade).contains("CAMARA") ? Poder.LEGISLATIVO : Poder.EXECUTIVO;
        for (String[] it : itens) {
            if (it[0].startsWith("LIMITE MAXIMO")) {
                try {
                    BigDecimal lim = valor(it[2].replace("%", ""));
                    for (Poder x : Poder.values()) {
                        if (x.limiteMaximo().compareTo(lim) == 0) {
                            poder = x;
                        }
                    }
                } catch (RelatorioInvalidoException ignorado) {
                    // o percentual não está nessa coluna; vale o palpite pelo nome da entidade
                }
                break;
            }
        }
        return new LinhaDespesa(codigo(codIbge, municipio), municipio, titulo(entidade), poder, ano, mes,
                bruta, ativo, inativos, outras, naoComputadas, dtp, rcl);
    }

    private static String rotulo(Map<String, String> rotulos, String chave) {
        String v = rotulos == null ? null : rotulos.get(chave);
        return v == null ? "" : limpar(v);
    }

    private static int inteiro(String t) {
        Matcher m = Pattern.compile("\\d{4}").matcher(t);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /** "Outubro" -> 10; "2º Quadrimestre" -> 8; "1º Semestre" -> 6; "10" -> 10. O período é o último mês coberto. */
    static int mesDoPeriodo(String t) {
        String x = sem(t);
        for (int i = 0; i < MESES.size(); i++) {
            if (x.startsWith(MESES.get(i).toUpperCase(Locale.ROOT)) || x.startsWith(
                    List.of("JANEIRO", "FEVEREIRO", "MARCO", "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO").get(i))) {
                return i + 1;
            }
        }
        Matcher n = Pattern.compile("(\\d+)").matcher(x);
        int num = n.find() ? Integer.parseInt(n.group(1)) : 0;
        if (x.contains("QUADRIMESTRE")) {
            return num >= 1 && num <= 3 ? num * 4 : 0;
        }
        if (x.contains("SEMESTRE")) {
            return num >= 1 && num <= 2 ? num * 6 : 0;
        }
        return num >= 1 && num <= 12 ? num : 0;
    }

    private static BigDecimal item(List<String[]> itens, boolean prefixo, String chave) {
        for (String[] it : itens) {
            if (prefixo ? it[0].startsWith(chave) : it[0].contains(chave)) {
                return valor(it[2]);
            }
        }
        throw new RelatorioInvalidoException("Item \"" + chave.toLowerCase(Locale.ROOT) + "\" não encontrado no CSV. Itens recebidos: "
                + itens.stream().map(i -> i[1]).distinct().limit(15).toList());
    }

    private static BigDecimal itemIgual(List<String[]> itens, String chave) {
        for (String[] it : itens) {
            if (it[0].equals(chave)) {
                return valor(it[2]);
            }
        }
        throw new RelatorioInvalidoException("Item \"" + chave.toLowerCase(Locale.ROOT) + "\" não encontrado no CSV. Itens recebidos: "
                + itens.stream().map(i -> i[1]).distinct().limit(15).toList());
    }

    private static BigDecimal itemOuZero(List<String[]> itens, String chave) {
        for (String[] it : itens) {
            if (it[0].startsWith(chave)) {
                return valor(it[2]);
            }
        }
        return BigDecimal.ZERO;
    }

    /** Aceita "1.234.567,89" (BR) e "1234567.89" (ponto decimal), com ou sem "R$". */
    static BigDecimal valor(String t) {
        String s = t.replace("R$", "").replace(" ", "").trim();
        if (s.contains(",")) {
            return numero(s);
        }
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            return new BigDecimal(s);
        }
        return numero(s);
    }

    static LinhaDespesa ler(List<List<String>> linhas, List<String> todas, String codIbge) {
        String tudo = sem(String.join(" ", todas));
        // o título pode vir partido em várias células; basta aparecer a despesa com pessoal e o relatório de gestão fiscal
        if (!tudo.contains("DESPESA COM PESSOAL") || !(tudo.contains("DEMONSTRATIVO") || tudo.contains("RELATORIO DE GESTAO FISCAL"))) {
            throw new RelatorioInvalidoException("O relatório recebido não é o Demonstrativo da Despesa com Pessoal (RGF - Anexo 1). "
                    + "O SIMAM devolveu: \"" + previa(todas) + "\".");
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

    /** Começo do texto recebido, para o erro mostrar o que o SIMAM realmente devolveu. */
    static String previa(List<String> textos) {
        String t = limpar(String.join(" | ", textos.stream().filter(x -> !x.isBlank()).limit(25).toList()));
        return t.isEmpty() ? "(vazio)" : t.length() > 300 ? t.substring(0, 300) + "…" : t;
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
