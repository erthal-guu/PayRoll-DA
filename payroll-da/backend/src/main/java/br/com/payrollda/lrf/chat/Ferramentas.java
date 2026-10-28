package br.com.payrollda.lrf.chat;

import java.util.List;
import java.util.Map;

/** Ferramentas oferecidas ao modelo (formato "tools" da API compatível com OpenAI, usada pelo Groq). */
final class Ferramentas {

    private Ferramentas() {
    }

    private static final Map<String, Object> FILTROS = Map.of(
            "ano", Map.of("type", "integer", "description", "Ano do período. Omitir = mais recente."),
            "mes", Map.of("type", "integer", "description", "Último mês do período de 12 meses (1 a 12)."),
            "poder", Map.of("type", "string", "enum", List.of("EXECUTIVO", "LEGISLATIVO")),
            "codIbge", Map.of("type", "string", "description", "Código IBGE do município, 7 dígitos."),
            "busca", Map.of("type", "string", "description", "Trecho do nome da entidade ou do município."));

    private static final List<Map<String, Object>> FUNCOES = List.of(
            Map.of("name", "resumo_painel",
                    "description", "Totais do período: quantidade de entidades, despesa total, uso médio do limite, "
                            + "quantas entidades em cada faixa (REGULAR, ALERTA, PRUDENCIAL, ACIMA_LIMITE) e composição da despesa.",
                    "parameters", Map.of("type", "object", "properties", FILTROS)),
            Map.of("name", "buscar_entidades",
                    "description", "Lista entidades com despesa total, RCL, percentual, limite e faixa. "
                            + "Use para perguntas como 'quais câmaras passaram do prudencial?'. Retorna no máximo 20.",
                    "parameters", Map.of("type", "object", "properties", comExtras(Map.of(
                            "faixa", Map.of("type", "string", "enum", List.of("REGULAR", "ALERTA", "PRUDENCIAL", "ACIMA_LIMITE")),
                            "ordem", Map.of("type", "string", "enum", List.of("usoLimite", "percentual", "despesaTotal", "entidade", "municipio")),
                            "direcao", Map.of("type", "string", "enum", List.of("asc", "desc")),
                            "limite", Map.of("type", "integer", "description", "Quantas linhas, até 20."))))),
            Map.of("name", "evolucao_entidade",
                    "description", "Série dos últimos 12 períodos de uma entidade: despesa, RCL, percentual e limite.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of("entidadeId", Map.of("type", "integer")),
                            "required", List.of("entidadeId"))),
            Map.of("name", "sugerir_ajuste",
                    "description", "Propõe um ajuste no painel que o usuário aplica com um botão: filtros, uma entidade "
                            + "para abrir no detalhe ou a tela a mostrar. Não muda nada sozinho.",
                    "parameters", Map.of("type", "object",
                            "properties", Map.of(
                                    "rotulo", Map.of("type", "string", "description", "Texto curto do botão, ex.: 'Ver câmaras no prudencial'."),
                                    "motivo", Map.of("type", "string", "description", "Uma frase dizendo por que ajuda."),
                                    "filtros", Map.of("type", "object", "properties", comExtras(Map.of(
                                            "faixa", Map.of("type", "string", "enum", List.of("REGULAR", "ALERTA", "PRUDENCIAL", "ACIMA_LIMITE"))))),
                                    "entidadeId", Map.of("type", "integer", "description", "Entidade a abrir no detalhe."),
                                    "tela", Map.of("type", "string", "enum", List.of("dashboard", "consultar", "importacao"))),
                            "required", List.of("rotulo"))));

    static final List<Map<String, Object>> DEFINICOES = FUNCOES.stream()
            .map(f -> Map.<String, Object>of("type", "function", "function", f))
            .toList();

    private static Map<String, Object> comExtras(Map<String, Object> extras) {
        var m = new java.util.HashMap<String, Object>(FILTROS);
        m.putAll(extras);
        return m;
    }
}
