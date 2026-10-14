package br.com.payrollda.lrf.importacao;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

    private final ImportacaoService service;
    private final ImportacaoRepository repo;

    public ImportacaoController(ImportacaoService service, ImportacaoRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    /** Envia um CSV exportado do TCE-PR. Responde 202 e processa em segundo plano. */
    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ImportacaoDto> upload(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return ResponseEntity.accepted().body(service.iniciarUpload(arquivo));
    }

    /** Baixa o arquivo de dados abertos do TCE-PR. Corpo opcional: {"url": "https://..."} */
    @PostMapping("/url")
    public ResponseEntity<ImportacaoDto> porUrl(@RequestBody(required = false) Map<String, String> corpo) {
        String url = corpo == null ? null : corpo.get("url");
        return ResponseEntity.accepted().body(service.iniciarDownload(url));
    }

    @GetMapping
    public List<ImportacaoDto> listar(@RequestParam(defaultValue = "20") int limite) {
        return repo.listar(Math.min(Math.max(limite, 1), 100));
    }

    @GetMapping("/{id}")
    public ImportacaoDto buscar(@PathVariable long id) {
        return repo.buscar(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada"));
    }

    @GetMapping("/{id}/erros")
    public List<ImportacaoDto.Erro> erros(@PathVariable long id) {
        return repo.erros(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> requisicaoInvalida(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
