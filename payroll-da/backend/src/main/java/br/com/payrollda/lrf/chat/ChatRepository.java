package br.com.payrollda.lrf.chat;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Conversas e mensagens do assistente (tabelas chat_conversa e chat_mensagem). */
@Repository
public class ChatRepository {

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Conversa(long id, String titulo, OffsetDateTime criadaEm, OffsetDateTime atualizadaEm, long mensagens) {
    }

    public record MensagemSalva(long id, String papel, String texto, String sugestoes, OffsetDateTime criadaEm) {
    }

    public List<Conversa> listar(long usuarioId) {
        return jdbc.query("""
                SELECT c.id, c.titulo, c.criada_em, c.atualizada_em,
                       (SELECT count(*) FROM chat_mensagem m WHERE m.conversa_id = c.id) AS mensagens
                FROM chat_conversa c WHERE c.usuario_id = ? ORDER BY c.atualizada_em DESC LIMIT 50""",
                (rs, n) -> new Conversa(rs.getLong("id"), rs.getString("titulo"),
                        rs.getObject("criada_em", OffsetDateTime.class), rs.getObject("atualizada_em", OffsetDateTime.class),
                        rs.getLong("mensagens")), usuarioId);
    }

    /** Confere se a conversa é do usuário. */
    public boolean ehDono(long conversaId, long usuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_conversa WHERE id = ? AND usuario_id = ?",
                Long.class, conversaId, usuarioId) > 0;
    }

    public long criar(long usuarioId, String titulo) {
        return jdbc.queryForObject("INSERT INTO chat_conversa (usuario_id, titulo) VALUES (?, ?) RETURNING id",
                Long.class, usuarioId, titulo);
    }

    public void adicionar(long conversaId, String papel, String texto, String sugestoesJson) {
        jdbc.update("INSERT INTO chat_mensagem (conversa_id, papel, texto, sugestoes) VALUES (?, ?, ?, CAST(? AS JSONB))",
                conversaId, papel, texto, sugestoesJson);
        jdbc.update("UPDATE chat_conversa SET atualizada_em = now() WHERE id = ?", conversaId);
    }

    public List<MensagemSalva> mensagens(long conversaId) {
        return jdbc.query("SELECT id, papel, texto, sugestoes::text AS sugestoes, criada_em FROM chat_mensagem "
                        + "WHERE conversa_id = ? ORDER BY id",
                (rs, n) -> new MensagemSalva(rs.getLong("id"), rs.getString("papel"), rs.getString("texto"),
                        rs.getString("sugestoes"), rs.getObject("criada_em", OffsetDateTime.class)), conversaId);
    }

    public boolean remover(long conversaId, long usuarioId) {
        return jdbc.update("DELETE FROM chat_conversa WHERE id = ? AND usuario_id = ?", conversaId, usuarioId) > 0;
    }
}
