package br.com.payrollda.lrf.professores;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.payrollda.lrf.auth.Usuario;
import br.com.payrollda.lrf.auth.UsuarioAtual;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Analise;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Nivel;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Piso;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Plano;
import br.com.payrollda.lrf.professores.ProfessoresDtos.PlanoResumo;

/**
 * Tela dos professores: piso do magistério e tabela de vencimentos do município. Os dois perfis consultam
 * (Prefeitura só o próprio município); só o administrador grava.
 */
@RestController
@RequestMapping("/api/professores")
public class ProfessoresController {

    private final ProfessoresRepository repo;

    public ProfessoresController(ProfessoresRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/pisos")
    public List<Piso> pisos() {
        return repo.pisos();
    }

    @PutMapping("/pisos/{ano}")
    public Piso salvarPiso(@PathVariable int ano, @RequestBody Piso p) {
        exigirAdmin();
        if (p == null || p.valor20h() == null || p.valor20h().signum() <= 0) {
            throw new IllegalArgumentException("Informe o valor do piso para 20 horas.");
        }
        Piso novo = new Piso(ano, p.valor20h(), vazioNulo(p.atoLegal()));
        repo.salvarPiso(novo);
        return novo;
    }

    @GetMapping("/planos")
    public List<PlanoResumo> planos() {
        return repo.planos(UsuarioAtual.codIbgeRestrito());
    }

    @GetMapping("/{codIbge}/{ano}")
    public ResponseEntity<Analise> analise(@PathVariable String codIbge, @PathVariable int ano,
                                           @RequestParam(defaultValue = "20") int carga) {
        exigirAcesso(codIbge);
        return repo.plano(codIbge, ano, carga)
                .map(pl -> ResponseEntity.ok(ProfessoresCalculo.analisar(pl, repo.piso(ano).orElse(null))))
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/{codIbge}/{ano}")
    public Analise salvarPlano(@PathVariable String codIbge, @PathVariable int ano, @RequestBody Plano p) {
        exigirAdmin();
        if (p == null || p.vencimentoBase() == null || p.vencimentoBase().signum() <= 0) {
            throw new IllegalArgumentException("Informe o vencimento base (classe 1-2 do primeiro nível).");
        }
        if (p.municipio() == null || p.municipio().isBlank()) {
            throw new IllegalArgumentException("Informe o município.");
        }
        if (p.niveis() == null || p.niveis().isEmpty() || p.niveis().stream().anyMatch(n -> n.nome() == null || n.nome().isBlank())) {
            throw new IllegalArgumentException("Informe ao menos um nível, todos com nome.");
        }
        int carga = p.cargaHoraria() > 0 ? p.cargaHoraria() : 20;
        int classeFinal = p.classeFinal() >= 2 && p.classeFinal() <= 60 ? p.classeFinal() : 30;
        BigDecimal percClasse = p.percClasse() == null ? BigDecimal.valueOf(2) : p.percClasse();
        List<Nivel> niveis = p.niveis().stream()
                .map(n -> new Nivel(n.nome().trim(), n.percSobreBase() == null ? BigDecimal.ZERO : n.percSobreBase())).toList();
        Plano novo = new Plano(codIbge, p.municipio().trim(), ano, carga, p.vencimentoBase(), percClasse, classeFinal,
                vazioNulo(p.lei()), niveis);
        repo.salvarPlano(novo);
        return ProfessoresCalculo.analisar(novo, repo.piso(ano).orElse(null));
    }

    @DeleteMapping("/{codIbge}/{ano}")
    public ResponseEntity<Void> removerPlano(@PathVariable String codIbge, @PathVariable int ano,
                                             @RequestParam(defaultValue = "20") int carga) {
        exigirAdmin();
        repo.removerPlano(codIbge, ano, carga);
        return ResponseEntity.noContent().build();
    }

    private static void exigirAdmin() {
        Usuario u = UsuarioAtual.get();
        if (u == null || !u.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o administrador pode fazer isso.");
        }
    }

    private static void exigirAcesso(String codIbge) {
        String restrito = UsuarioAtual.codIbgeRestrito();
        if (restrito != null && !restrito.equals(codIbge)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode ver o seu município.");
        }
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
