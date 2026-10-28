package br.com.payrollda.lrf.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login por e-mail e senha. O navegador recebe um token aleatório; o banco guarda só o hash dele.
 * Na primeira subida, sem nenhum usuário cadastrado, cria o administrador inicial.
 */
@Service
public class AuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int HORAS_SESSAO = 12;

    private final UsuarioRepository repo;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom aleatorio = new SecureRandom();

    @Value("${payroll.admin.email:admin@payroll.local}")
    private String adminEmail;

    @Value("${payroll.admin.senha:admin}")
    private String adminSenha;

    public AuthService(UsuarioRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.contar() == 0) {
            repo.criar("Administrador", adminEmail, bcrypt.encode(adminSenha), "ADMIN", null, null);
            log.warn("Nenhum usuário cadastrado: criado o administrador {}. Troque a senha na tela Usuários.", adminEmail);
        }
    }

    public record Sessao(String token, Usuario usuario) {
    }

    public Optional<Sessao> entrar(String email, String senha) {
        if (email == null || senha == null) {
            return Optional.empty();
        }
        return repo.buscarParaLogin(email.trim())
                .filter(u -> bcrypt.matches(senha, u.senhaHash()))
                .map(u -> {
                    byte[] bytes = new byte[32];
                    aleatorio.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    repo.criarSessao(hash(token), u.usuario().id(), OffsetDateTime.now().plusHours(HORAS_SESSAO));
                    return new Sessao(token, u.usuario());
                });
    }

    public Optional<Usuario> validar(String token) {
        return token == null || token.isBlank() ? Optional.empty() : repo.buscarPorSessao(hash(token));
    }

    public void sair(String token) {
        if (token != null) {
            repo.removerSessao(hash(token));
        }
    }

    public Usuario criarUsuario(String nome, String email, String senha, String perfil, String codIbge, String municipio) {
        if (nome == null || nome.isBlank() || email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Informe nome e um e-mail válido.");
        }
        validarSenha(senha);
        String p = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        if (!p.equals("ADMIN") && !p.equals("PREFEITURA")) {
            throw new IllegalArgumentException("Perfil deve ser ADMIN ou PREFEITURA.");
        }
        if (p.equals("PREFEITURA") && (codIbge == null || codIbge.isBlank())) {
            throw new IllegalArgumentException("Escolha o município do usuário da prefeitura.");
        }
        if (repo.emailEmUso(email.trim())) {
            throw new IllegalArgumentException("Já existe um usuário com esse e-mail.");
        }
        boolean admin = p.equals("ADMIN");
        long id = repo.criar(nome.trim(), email.trim(), bcrypt.encode(senha), p,
                admin ? null : codIbge.trim(), admin ? null : municipio);
        return new Usuario(id, nome.trim(), email.trim(), p, admin ? null : codIbge.trim(), admin ? null : municipio, true);
    }

    public void trocarSenha(long usuarioId, String novaSenha) {
        validarSenha(novaSenha);
        repo.trocarSenha(usuarioId, bcrypt.encode(novaSenha));
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < 6) {
            throw new IllegalArgumentException("A senha precisa ter pelo menos 6 caracteres.");
        }
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
