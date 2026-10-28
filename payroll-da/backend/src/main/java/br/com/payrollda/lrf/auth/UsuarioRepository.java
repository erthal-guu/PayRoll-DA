package br.com.payrollda.lrf.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {

    private static final String COLUNAS = "u.id, u.nome, u.email, u.perfil, u.cod_ibge, u.municipio, u.ativo";
    private static final RowMapper<Usuario> MAPPER = (rs, n) -> new Usuario(rs.getLong("id"), rs.getString("nome"),
            rs.getString("email"), rs.getString("perfil"), rs.getString("cod_ibge"), rs.getString("municipio"),
            rs.getBoolean("ativo"));

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long contar() {
        return jdbc.queryForObject("SELECT count(*) FROM usuario", Long.class);
    }

    public List<Usuario> listar() {
        return jdbc.query("SELECT " + COLUNAS + " FROM usuario u ORDER BY u.nome", MAPPER);
    }

    /** Usuário ativo e o hash da senha, para conferir o login. */
    public Optional<UsuarioComSenha> buscarParaLogin(String email) {
        return jdbc.query("SELECT " + COLUNAS + ", u.senha_hash FROM usuario u WHERE lower(u.email) = lower(?) AND u.ativo",
                (rs, n) -> new UsuarioComSenha(MAPPER.mapRow(rs, n), rs.getString("senha_hash")), email).stream().findFirst();
    }

    public record UsuarioComSenha(Usuario usuario, String senhaHash) {
    }

    public boolean emailEmUso(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM usuario WHERE lower(email) = lower(?)", Long.class, email) > 0;
    }

    public long criar(String nome, String email, String senhaHash, String perfil, String codIbge, String municipio) {
        return jdbc.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, perfil, cod_ibge, municipio)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, nome, email, senhaHash, perfil, codIbge, municipio);
    }

    public void trocarSenha(long id, String senhaHash) {
        jdbc.update("UPDATE usuario SET senha_hash = ? WHERE id = ?", senhaHash, id);
        jdbc.update("DELETE FROM sessao WHERE usuario_id = ?", id);
    }

    public boolean remover(long id) {
        return jdbc.update("DELETE FROM usuario WHERE id = ?", id) > 0;
    }

    public void criarSessao(String tokenHash, long usuarioId, OffsetDateTime expiraEm) {
        jdbc.update("DELETE FROM sessao WHERE expira_em < now()");
        jdbc.update("INSERT INTO sessao (token_hash, usuario_id, expira_em) VALUES (?, ?, ?)", tokenHash, usuarioId, expiraEm);
    }

    public Optional<Usuario> buscarPorSessao(String tokenHash) {
        return jdbc.query("SELECT " + COLUNAS + " FROM sessao s JOIN usuario u ON u.id = s.usuario_id"
                + " WHERE s.token_hash = ? AND s.expira_em > now() AND u.ativo", MAPPER, tokenHash).stream().findFirst();
    }

    public void removerSessao(String tokenHash) {
        jdbc.update("DELETE FROM sessao WHERE token_hash = ?", tokenHash);
    }
}
