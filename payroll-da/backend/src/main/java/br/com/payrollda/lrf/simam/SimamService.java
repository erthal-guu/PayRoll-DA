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
    private final Path diagnostico;

    public SimamService(SimamClient client, ImportacaoService importacao, ImportacaoRepository importacoes,
                        DashboardRepository dashboard,
                        @Value("${tce.simam.diagnostico:${java.io.tmpdir}/simam-diagnostico}") String diagnostico) {
        this.client = client;
        this.importacao = importacao;
        this.importacoes = importacoes;
        this.dashboard = dashboard;
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
        if (selecao.get("periodo") != null && !selecao.get("periodo").isBlank()) {
            return consultarPeriodo(selecao);
        }
        List<SimamClient.Opcao> periodos = client.filtros(selecao).stream()
                .filter(f -> f.chave().equals("periodo") && f.habilitado())
                .findFirst().map(SimamClient.Filtro::opcoes).orElse(List.of());
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
