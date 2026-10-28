package br.com.payrollda.lrf.auth;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de usuários (só ADMIN, ver AuthFilter). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository repo;
    private final AuthService auth;

    public UsuarioController(UsuarioRepository repo, AuthService auth) {
        this.repo = repo;
        this.auth = auth;
    }

    public record NovoUsuario(String nome, String email, String senha, String perfil, String codIbge, String municipio) {
    }

    @GetMapping
    public List<Usuario> listar() {
        return repo.listar();
    }

    @PostMapping
    public Usuario criar(@RequestBody NovoUsuario n) {
        return auth.criarUsuario(n.nome(), n.email(), n.senha(), n.perfil(), n.codIbge(), n.municipio());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        if (id == UsuarioAtual.get().id()) {
            throw new IllegalArgumentException("Você não pode remover o próprio usuário.");
        }
        return repo.remover(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
