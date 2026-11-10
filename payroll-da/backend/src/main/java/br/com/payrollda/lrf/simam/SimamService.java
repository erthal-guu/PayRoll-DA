package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.payrollda.lrf.consulta.ConsultaController.Resposta;
import br.com.payrollda.lrf.dashboard.DashboardRepository;
import br.com.payrollda.lrf.fundeb.FundebRepository;
import br.com.payrollda.lrf.importacao.ImportacaoRepository;
import br.com.payrollda.lrf.importacao.ImportacaoService;
import br.com.payrollda.lrf.importacao.LinhaDespesa;

/** Consulta automática: busca o relatório no SIMAM com os filtros escolhidos e grava na base. */
@Service
public class SimamService {

    private static final Logger log = LoggerFactory.getLogger(SimamService.class);

    private final SimamClient client;
    private final ImportacaoService importacao;
    private final ImportacaoRepository importacoes;
    private final DashboardRepository dashboard;
    private final FundebRepository fundeb;
    private final Path diagnostico;

    public SimamService(SimamClient client, ImportacaoService importacao, ImportacaoRepository importacoes,
                        DashboardRepository dashboard, FundebRepository fundeb,
                        @Value("${tce.simam.diagnostico:${java.io.tmpdir}/simam-diagnostico}") String diagnostico) {
        this.client = client;
        this.importacao = importacao;
        this.importacoes = importacoes;
        this.dashboard = dashboard;
        this.fundeb = fundeb;
        this.diagnostico = Path.of(diagnostico);
    }

    public List<SimamClient.Filtro> filtros(Map<String, String> selecao) throws IOException, InterruptedException {
        return client.filtros(selecao);
    }

    /**
     * Sem período na seleção, importa todos os períodos que o SIMAM oferece para o ano escolhido e devolve o último
     * que trouxe demonstrativo (o mais recente). Com período, faz a consulta de um período só.
     */
    public Resposta consultar(Map<String, String> selecao) throws IOException, InterruptedException {
        List<SimamClient.Filtro> filtros = client.filtros(selecao);
        boolean mde = filtros.stream().filter(f -> f.chave().equals("relatorio")).findFirst()
                .flatMap(f -> f.opcoes().stream().filter(o -> o.valor().equals(selecao.get("relatorio"))).findFirst())
                .map(o -> RelatorioRgfParser.sem(o.rotulo()).contains("DESENVOLVIMENTO DO ENSINO")).orElse(false);
        if (selecao.get("periodo") != null && !selecao.get("periodo").isBlank()) {
            return mde ? consultarMde(selecao, List.of()) : consultarPeriodo(selecao);
        }
        java.time.LocalDate hoje = java.time.LocalDate.now();
        boolean anoCorrente = String.valueOf(hoje.getYear()).equals(selecao.get("ano"));
        // no ano corrente, os meses que ainda não terminaram não têm relatório publicado
        List<SimamClient.Opcao> periodos = filtros.stream()
                .filter(f -> f.chave().equals("periodo") && f.habilitado())
                .findFirst().map(SimamClient.Filtro::opcoes).orElse(List.of()).stream()
                .filter(o -> !anoCorrente || RelatorioRgfParser.mesDoPeriodo(o.rotulo()) < hoje.getMonthValue())
                .toList();
        if (mde) {
            return consultarMde(selecao, periodos);
        }
        if (periodos.isEmpty()) {
            return consultarPeriodo(selecao);
        }
        Resposta ultima = null;
        int mesUltima = -1;
        List<String> importados = new java.util.ArrayList<>();
        List<String> falhas = new java.util.ArrayList<>();
        for (SimamClient.Opcao p : periodos) {
            Map<String, String> sel = new java.util.LinkedHashMap<>(selecao);
            sel.put("periodo", p.valor());
            Resposta r;
            try {
                r = consultarPeriodo(sel);
            } catch (IOException | RuntimeException e) {
                log.warn("SIMAM: período {} não foi importado", p.rotulo(), e);
                falhas.add(p.rotulo().trim() + " (" + e.getMessage() + ")");
                continue;
            }
            if (r.demonstrativo() == null) {
                falhas.add(p.rotulo().trim());
                if (ultima == null) {
                    ultima = r;
                }
                continue;
            }
            importados.add(p.rotulo().trim());
            int mes = r.demonstrativo().mes();
            if (ultima == null || ultima.demonstrativo() == null || mes > mesUltima) {
                ultima = r;
                mesUltima = mes;
            }
        }
        if (ultima == null) {
            throw new IOException("nenhum período do ano foi importado: " + String.join("; ", falhas));
        }
        String msg = (importados.isEmpty() ? "Nenhum período importado." : "Períodos importados: " + String.join(", ", importados) + ".")
                + (falhas.isEmpty() ? "" : " Sem demonstrativo ou com falha: " + String.join("; ", falhas) + ".");
        return new Resposta(ultima.importacao(), ultima.demonstrativo(), ultima.excelUrl(), msg);
    }

    private Resposta consultarPeriodo(Map<String, String> selecao) throws IOException, InterruptedException {
        SimamClient.Retorno r = client.consultar(selecao);
        String codIbge = selecao.get("municipio");
        LinhaDespesa linha = null;
        String motivo = "";
        if (r.csv() != null) {
            try {
                linha = RelatorioRgfParser.ehCsvDados(r.csv())
                        ? RelatorioRgfParser.deCsvDados(r.csv(), r.rotulos(), codIbge)
                        : RelatorioRgfParser.deCsv(r.csv(), codIbge);
            } catch (RuntimeException | IOException e) {
                motivo = "CSV: " + e.getMessage();
            }
        }
        if (linha == null) {
            try {
                linha = RelatorioRgfParser.deHtml(r.htmlVisualizacao(), codIbge);
            } catch (RuntimeException e) {
                motivo += (motivo.isEmpty() ? "" : " | ") + "HTML: " + e.getMessage();
            }
        }
        if (linha == null) {
            Path pasta = salvarDiagnostico(r);
            long id = importacoes.criar("SIMAM", resumo(selecao));
            String msg = "O SIMAM respondeu, mas não consegui ler o relatório (" + motivo + "). "
                    + (r.csv() == null ? "O SIMAM não ofereceu o CSV. " : "")
                    + "Texto da página recebida: \"" + textoDaPagina(r.htmlVisualizacao()) + "\". Amostras salvas em " + pasta + ".";
            importacoes.finalizar(id, "FALHOU", 0, 0, 0, msg);
            return new Resposta(importacoes.buscar(id).orElseThrow(), null, null, msg);
        }
        long id = importacoes.criar("SIMAM", resumo(selecao));
        var dto = importacao.importarLinhas(id, List.of(linha));
        long entidadeId = importacoes.idEntidade(linha);
        var d = dashboard.demonstrativo(entidadeId, linha.ano(), linha.mes()).orElse(null);
        String excel = d == null ? null : "/api/consultas/excel?entidadeId=" + entidadeId + "&ano=" + linha.ano() + "&mes=" + linha.mes();
        return new Resposta(dto, d, excel, d != null ? "Consulta concluída." : "Importado, mas sem demonstrativo para o período.");
    }

    /**
     * RREO MDE (transferências do FUNDEB): os valores são acumulados no ano, então basta o período mais recente que o
     * SIMAM tiver publicado. Tenta do último para o primeiro e para no primeiro que vier com o bloco do FUNDEB.
     */
    private Resposta consultarMde(Map<String, String> selecao, List<SimamClient.Opcao> periodos) throws IOException, InterruptedException {
        List<Map<String, String>> tentativas = new java.util.ArrayList<>();
        if (periodos.isEmpty()) {
            tentativas.add(selecao);
        }
        for (int i = periodos.size() - 1; i >= 0; i--) {
            Map<String, String> sel = new java.util.LinkedHashMap<>(selecao);
            sel.put("periodo", periodos.get(i).valor());
            tentativas.add(sel);
        }
        List<String> falhas = new java.util.ArrayList<>();
        for (Map<String, String> sel : tentativas) {
            SimamClient.Retorno r;
            try {
                r = client.consultar(sel);
            } catch (IOException | RuntimeException e) {
                log.warn("SIMAM: MDE do período {} não foi consultado", sel.get("periodo"), e);
                falhas.add(e.getMessage());
                continue;
            }
            String periodo = r.rotulos().getOrDefault("periodo", "").trim();
            RelatorioMdeParser.Fundeb f = null;
            String motivo = "";
            if (r.csv() != null) {
                try {
                    f = RelatorioMdeParser.deCsv(r.csv());
                } catch (RuntimeException e) {
                    motivo = "CSV: " + e.getMessage();
                }
            }
            if (f == null) {
                try {
                    f = RelatorioMdeParser.deHtml(r.htmlVisualizacao());
                } catch (RuntimeException e) {
                    motivo += (motivo.isEmpty() ? "" : " | ") + "HTML: " + e.getMessage();
                }
            }
            if (f == null) {
                salvarDiagnostico(r);
                falhas.add((periodo.isEmpty() ? "período" : periodo) + ": " + motivo);
                continue;
            }
            String codIbge = selecao.get("municipio");
            String municipio = RelatorioRgfParser.titulo(r.rotulos().getOrDefault("municipio", codIbge).trim());
            int ano = Integer.parseInt(r.rotulos().getOrDefault("ano", selecao.get("ano")).replaceAll("\\D", ""));
            int mes = RelatorioRgfParser.mesDoPeriodo(periodo);
            fundeb.salvar(codIbge, municipio, ano, periodo.isEmpty() ? "-" : periodo, mes == 0 ? 12 : mes, f);
            long id = importacoes.criar("SIMAM", resumo(sel));
            String msg = "Transferências do FUNDEB importadas (" + municipio + ", " + ano + (periodo.isEmpty() ? "" : ", " + periodo) + ").";
            importacoes.finalizar(id, "CONCLUIDA", 1, 1, 0, msg);
            FundebRepository.Ano gravado = fundeb.anos(codIbge).stream().filter(a -> a.ano() == ano).findFirst().orElse(null);
            return new Resposta(importacoes.buscar(id).orElseThrow(), null, null, msg, gravado);
        }
        long id = importacoes.criar("SIMAM", resumo(selecao));
        String msg = "Não consegui ler o FUNDEB do demonstrativo MDE em nenhum período do ano: " + String.join("; ", falhas)
                + ". Amostras salvas em " + diagnostico + ".";
        importacoes.finalizar(id, "FALHOU", 0, 0, 0, msg);
        return new Resposta(importacoes.buscar(id).orElseThrow(), null, null, msg);
    }

    /** Título e começo do texto visível da página, para o erro mostrar o que o SIMAM disse (ex.: "relatório não gerado"). */
    private static String textoDaPagina(String html) {
        try {
            var doc = org.jsoup.Jsoup.parse(html == null ? "" : html);
            doc.select("script, style").remove();
            String t = (doc.title() + " | " + doc.body().text()).replaceAll("\\s+", " ").trim();
            return t.length() > 400 ? t.substring(0, 400) + "…" : t;
        } catch (RuntimeException e) {
            return "(não consegui extrair o texto)";
        }
    }

    private static String resumo(Map<String, String> selecao) {
        return "SIMAM " + selecao;
    }

    private Path salvarDiagnostico(SimamClient.Retorno r) {
        try {
            Files.createDirectories(diagnostico);
            String carimbo = String.valueOf(Instant.now().toEpochMilli());
            Files.writeString(diagnostico.resolve("visualizacao-" + carimbo + ".html"), r.htmlVisualizacao(), StandardOpenOption.CREATE);
            if (r.csv() != null) {
                Files.writeString(diagnostico.resolve("exportacao-" + carimbo + ".csv"), r.csv(), StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            log.warn("Não foi possível salvar o diagnóstico do SIMAM", e);
        }
        return diagnostico;
    }
}
