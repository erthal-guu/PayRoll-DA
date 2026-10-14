package br.com.payrollda.lrf.importacao;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import br.com.payrollda.lrf.config.ImportacaoProperties;
import br.com.payrollda.lrf.importacao.LinhaDespesa.Poder;

/**
 * Lê o CSV linha a linha (sem carregar o arquivo inteiro na memória) e localiza
 * as colunas pelo nome do cabeçalho configurado em {@code tce.importacao.colunas}.
 */
@Component
public class CsvDespesaParser {

    static final List<String> OBRIGATORIAS = List.of(
            "municipio", "cod-ibge", "entidade", "poder", "ano", "mes", "despesa-total", "rcl-ajustada");

    private final ImportacaoProperties props;

    public CsvDespesaParser(ImportacaoProperties props) {
        this.props = props;
    }

    public interface Ouvinte {
        void linhaValida(LinhaDespesa linha);

        void linhaInvalida(long numeroLinha, String motivo);
    }

    /** @return quantidade de linhas de dados lidas */
    public long ler(Reader reader, Ouvinte ouvinte) throws IOException {
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(props.delimitador())
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = formato.parse(reader)) {
            Map<String, Integer> indices = localizarColunas(parser.getHeaderNames());
            long lidas = 0;
            for (CSVRecord registro : parser) {
                lidas++;
                long numeroLinha = registro.getRecordNumber() + 1; // +1 por causa do cabeçalho
                try {
                    ouvinte.linhaValida(converter(registro, indices));
                } catch (IllegalArgumentException e) {
                    ouvinte.linhaInvalida(numeroLinha, e.getMessage());
                }
            }
            return lidas;
        }
    }

    Map<String, Integer> localizarColunas(List<String> cabecalho) {
        Map<String, Integer> porNome = new HashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            porNome.put(normalizar(cabecalho.get(i)), i);
        }
        Map<String, Integer> indices = new HashMap<>();
        List<String> faltando = new ArrayList<>();
        props.colunas().forEach((campo, coluna) -> {
            Integer idx = porNome.get(normalizar(coluna));
            if (idx != null) {
                indices.put(campo, idx);
            } else if (OBRIGATORIAS.contains(campo)) {
                faltando.add(coluna);
            }
        });
        if (!faltando.isEmpty()) {
            throw new FormatoInvalidoException("O arquivo não tem as colunas obrigatórias: " + String.join(", ", faltando)
                    + ". Confira o cabeçalho do CSV ou ajuste tce.importacao.colunas no application.yml.");
        }
        return indices;
    }

    private LinhaDespesa converter(CSVRecord r, Map<String, Integer> idx) {
        String codIbge = texto(r, idx, "cod-ibge").replaceAll("\\D", "");
        if (codIbge.length() != 7) {
            throw new IllegalArgumentException("Código IBGE inválido: \"" + texto(r, idx, "cod-ibge") + "\" (esperado 7 dígitos)");
        }
        int mes = inteiro(r, idx, "mes");
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mês fora do intervalo 1 a 12: " + mes);
        }
        BigDecimal total = valor(r, idx, "despesa-total", true);
        return new LinhaDespesa(
                codIbge,
                obrigatorio(r, idx, "municipio"),
                obrigatorio(r, idx, "entidade"),
                poder(texto(r, idx, "poder")),
                inteiro(r, idx, "ano"),
                mes,
                valorOu(r, idx, "despesa-bruta", total),
                valorOu(r, idx, "pessoal-ativo", BigDecimal.ZERO),
                valorOu(r, idx, "inativos-pensionistas", BigDecimal.ZERO),
                valorOu(r, idx, "outras-despesas", BigDecimal.ZERO),
                valorOu(r, idx, "nao-computadas", BigDecimal.ZERO),
                total,
                valor(r, idx, "rcl-ajustada", true));
    }

    static Poder poder(String valor) {
        String v = normalizar(valor);
        if (v.startsWith("exec") || v.contains("prefeitura")) {
            return Poder.EXECUTIVO;
        }
        if (v.startsWith("legis") || v.contains("camara")) {
            return Poder.LEGISLATIVO;
        }
        throw new IllegalArgumentException("Poder não reconhecido: \"" + valor + "\" (use Executivo ou Legislativo)");
    }

    /** Aceita "1.234.567,89", "1234567.89", "R$ 1.234,00" e "(1.234,00)" para negativos. */
    static BigDecimal numeroBr(String bruto) {
        String s = bruto.replace("R$", "").replace("%", "").replace(" ", "").trim();
        boolean negativo = s.startsWith("(") && s.endsWith(")");
        s = s.replace("(", "").replace(")", "").replace(" ", "");
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        BigDecimal n = new BigDecimal(s);
        return negativo ? n.negate() : n;
    }

    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "").replace("﻿", "");
    }

    private String texto(CSVRecord r, Map<String, Integer> idx, String campo) {
        Integer i = idx.get(campo);
        return i == null || i >= r.size() ? "" : r.get(i);
    }

    private String obrigatorio(CSVRecord r, Map<String, Integer> idx, String campo) {
        String v = texto(r, idx, campo);
        if (v.isBlank()) {
            throw new IllegalArgumentException("Campo \"" + props.colunas().get(campo) + "\" vazio");
        }
        return v;
    }

    private int inteiro(CSVRecord r, Map<String, Integer> idx, String campo) {
        String v = obrigatorio(r, idx, campo);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inteiro inválido em \"" + props.colunas().get(campo) + "\": " + v);
        }
    }

    private BigDecimal valor(CSVRecord r, Map<String, Integer> idx, String campo, boolean exigido) {
        String v = exigido ? obrigatorio(r, idx, campo) : texto(r, idx, campo);
        try {
            return numeroBr(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido em \"" + props.colunas().get(campo) + "\": " + v);
        }
    }

    private BigDecimal valorOu(CSVRecord r, Map<String, Integer> idx, String campo, BigDecimal padrao) {
        return texto(r, idx, campo).isBlank() ? padrao : valor(r, idx, campo, false);
    }

    /** Problema no arquivo como um todo (cabeçalho errado), não em uma linha. */
    public static class FormatoInvalidoException extends RuntimeException {
        public FormatoInvalidoException(String mensagem) {
            super(mensagem);
        }
    }
}
