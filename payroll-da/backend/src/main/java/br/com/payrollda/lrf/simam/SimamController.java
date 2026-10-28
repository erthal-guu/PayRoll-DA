package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.payrollda.lrf.consulta.ConsultaController.Resposta;

@RestController
@RequestMapping("/api/simam")
public class SimamController {

    private final SimamService service;
    private final SimamClient client;

    public SimamController(SimamService service, SimamClient client) {
        this.service = service;
        this.client = client;
    }

    public record Pedido(Map<String, String> selecao) {
    }

    /** Filtros do formulário do SIMAM (tipo, município, entidade, relatório, ano, período) com as opções de cada um. */
    @PostMapping("/filtros")
    public List<SimamClient.Filtro> filtros(@RequestBody(required = false) Pedido p) throws IOException, InterruptedException {
        return service.filtros(p == null ? Map.of() : p.selecao());
    }

    @PostMapping("/consultar")
    public Resposta consultar(@RequestBody Pedido p) throws IOException, InterruptedException {
        if (p == null || p.selecao() == null) {
            throw new IllegalArgumentException("Informe os filtros da consulta.");
        }
        return service.consultar(p.selecao());
    }

    /** Páginas que o SIMAM devolveu na última operação, para entender quando o formulário muda. */
    @org.springframework.web.bind.annotation.GetMapping("/diagnostico")
    public ResponseEntity<byte[]> diagnostico() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"simam-diagnostico.zip\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(client.diagnosticoZip());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> falhaNoSimam(IOException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("mensagem", "Não foi possível consultar o SIMAM: " + motivo(e)));
    }

    /** Explica em português as falhas de rede mais comuns (algumas exceções vêm sem mensagem). */
    static String motivo(IOException e) {
        if (e instanceof java.net.http.HttpTimeoutException) {
            return (e.getMessage() == null ? "o site do TCE-PR demorou demais para responder" : e.getMessage())
                    + ". Tente de novo em alguns minutos.";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
            return "não consegui conectar em simam.tce.pr.gov.br. Confira a internet, o firewall ou o proxy deste computador.";
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return "falha no certificado HTTPS do TCE-PR (" + e.getMessage() + ").";
        }
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }
}
