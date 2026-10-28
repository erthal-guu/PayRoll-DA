package br.com.payrollda.lrf.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record Login(String email, String senha) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Login l) {
        return auth.entrar(l.email(), l.senha())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "E-mail ou senha incorretos.")));
    }

    @GetMapping("/eu")
    public Usuario eu() {
        return UsuarioAtual.get();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        auth.sair(AuthFilter.token(req));
        return ResponseEntity.noContent().build();
    }

    public record NovaSenha(String senha) {
    }

    @PostMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@RequestBody NovaSenha s) {
        auth.trocarSenha(UsuarioAtual.get().id(), s.senha());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
