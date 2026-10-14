package br.com.payrollda.lrf.consulta;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.payrollda.lrf.config.ImportacaoProperties;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Demonstrativo;
import br.com.payrollda.lrf.dashboard.DashboardRepository;
import br.com.payrollda.lrf.importacao.ImportacaoDto;
import br.com.payrollda.lrf.importacao.ImportacaoService;

/**
 * Botão "Consultar": (1) busca o CSV do TCE-PR e importa no banco, quando
 * tce.importacao.url-consulta está configurado; (2) devolve o demonstrativo;
 * (3) libera o Excel em GET /api/consultas/excel.
 */
@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportacaoService importacao;
    private final ImportacaoProperties props;
    private final DashboardRepository dashboard;
    private final ExcelService excel;

    public ConsultaController(ImportacaoService importacao, ImportacaoProperties props,
                              DashboardRepository dashboard, ExcelService excel) {
        this.importacao = importacao;
        this.props = props;
        this.dashboard = dashboard;
        this.excel = excel;
    }

    public record Pedido(String codIbge, Long entidadeId, Integer ano, Integer mes) {
    }

    public record Resposta(ImportacaoDto importacao, Demonstrativo demonstrativo, String excelUrl, String mensagem) {
    }

    @PostMapping
    public Resposta consultar(@RequestBody Pedido p) {
        if (p == null || p.codIbge() == null || p.entidadeId() == null || p.ano() == null || p.mes() == null) {
            throw new IllegalArgumentException("Informe município, entidade, ano e período.");
        }
        ImportacaoDto imp = null;
        if (props.urlConsulta() != null && !props.urlConsulta().isBlank()) {
            String url = props.urlConsulta()
                    .replace("{codIbge}", p.codIbge())
                    .replace("{ano}", String.valueOf(p.ano()))
                    .replace("{mes}", String.format("%02d", p.mes()));
            imp = importacao.importarAgora(url);
        }
        Demonstrativo d = dashboard.demonstrativo(p.entidadeId(), p.ano(), p.mes()).orElse(null);
        String excelUrl = d == null ? null
                : "/api/consultas/excel?entidadeId=" + p.entidadeId() + "&ano=" + p.ano() + "&mes=" + p.mes();
        String msg = d != null ? "Consulta concluída."
                : imp != null && "FALHOU".equals(imp.status()) ? imp.mensagem()
                : "Não há dados dessa entidade para o período escolhido.";
        return new Resposta(imp, d, excelUrl, msg);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam long entidadeId, @RequestParam int ano, @RequestParam int mes)
            throws IOException {
        Demonstrativo d = dashboard.demonstrativo(entidadeId, ano, mes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sem dados para esse período"));
        byte[] arquivo = excel.gerar(d, dashboard.evolucaoEntidade(entidadeId));
        String nome = String.format("RGF_pessoal_%s_%d-%02d.xlsx", d.municipio().replaceAll("[^\\p{L}0-9]+", "_"), ano, mes);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(arquivo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> requisicaoInvalida(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
