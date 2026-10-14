package br.com.payrollda.lrf.importacao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ImportacaoRepository {

    private final JdbcTemplate jdbc;
    /** Cache de entidades durante a importação: "codIbge|nome" -> id. */
    private final Map<String, Long> cacheEntidades = new HashMap<>();

    public ImportacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long criar(String origem, String referencia) {
        return jdbc.queryForObject(
                "INSERT INTO importacao (origem, referencia, status) VALUES (?, ?, 'PROCESSANDO') RETURNING id",
                Long.class, origem, referencia);
    }

    public void finalizar(long id, String status, long lidas, long importadas, long erros, String mensagem) {
        jdbc.update("""
                UPDATE importacao SET status = ?, linhas_lidas = ?, linhas_importadas = ?, linhas_com_erro = ?,
                       mensagem = ?, concluida_em = now()
                 WHERE id = ?""", status, lidas, importadas, erros, mensagem, id);
    }

    public void atualizarProgresso(long id, long lidas, long importadas, long erros) {
        jdbc.update("UPDATE importacao SET linhas_lidas = ?, linhas_importadas = ?, linhas_com_erro = ? WHERE id = ?",
                lidas, importadas, erros, id);
    }

    public void registrarErro(long importacaoId, long linha, String mensagem) {
        jdbc.update("INSERT INTO importacao_erro (importacao_id, linha, mensagem) VALUES (?, ?, ?)",
                importacaoId, linha, mensagem);
    }

    public synchronized long idEntidade(LinhaDespesa l) {
        String chave = l.codIbge() + "|" + l.entidade();
        return cacheEntidades.computeIfAbsent(chave, k -> jdbc.queryForObject("""
                INSERT INTO entidade (cod_ibge, municipio, nome, poder) VALUES (?, ?, ?, ?)
                ON CONFLICT (cod_ibge, nome) DO UPDATE SET municipio = EXCLUDED.municipio, poder = EXCLUDED.poder
                RETURNING id""", Long.class, l.codIbge(), l.municipio(), l.entidade(), l.poder().name()));
    }

    public void limparCache() {
        cacheEntidades.clear();
    }

    /** Insere ou atualiza o período (reimportar o mesmo arquivo não duplica dados). */
    public void gravarLote(long importacaoId, List<LinhaDespesa> lote) {
        jdbc.batchUpdate("""
                INSERT INTO despesa_pessoal (entidade_id, ano, mes, despesa_bruta, pessoal_ativo, inativos_pensionistas,
                       outras_despesas, nao_computadas, despesa_total, rcl_ajustada, percentual, limite_maximo, importacao_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (entidade_id, ano, mes) DO UPDATE SET
                       despesa_bruta = EXCLUDED.despesa_bruta, pessoal_ativo = EXCLUDED.pessoal_ativo,
                       inativos_pensionistas = EXCLUDED.inativos_pensionistas, outras_despesas = EXCLUDED.outras_despesas,
                       nao_computadas = EXCLUDED.nao_computadas, despesa_total = EXCLUDED.despesa_total,
                       rcl_ajustada = EXCLUDED.rcl_ajustada, percentual = EXCLUDED.percentual,
                       limite_maximo = EXCLUDED.limite_maximo, importacao_id = EXCLUDED.importacao_id,
                       atualizado_em = now()""",
                lote, lote.size(), (ps, l) -> {
                    ps.setLong(1, idEntidade(l));
                    ps.setInt(2, l.ano());
                    ps.setInt(3, l.mes());
                    ps.setBigDecimal(4, l.despesaBruta());
                    ps.setBigDecimal(5, l.pessoalAtivo());
                    ps.setBigDecimal(6, l.inativosPensionistas());
                    ps.setBigDecimal(7, l.outrasDespesas());
                    ps.setBigDecimal(8, l.naoComputadas());
                    ps.setBigDecimal(9, l.despesaTotal());
                    ps.setBigDecimal(10, l.rclAjustada());
                    ps.setBigDecimal(11, l.percentual());
                    ps.setBigDecimal(12, l.poder().limiteMaximo());
                    ps.setLong(13, importacaoId);
                });
    }

    public List<ImportacaoDto> listar(int limite) {
        return jdbc.query("SELECT * FROM importacao ORDER BY id DESC LIMIT ?", MAPPER, limite);
    }

    public Optional<ImportacaoDto> buscar(long id) {
        return jdbc.query("SELECT * FROM importacao WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<ImportacaoDto.Erro> erros(long id) {
        return jdbc.query("SELECT linha, mensagem FROM importacao_erro WHERE importacao_id = ? ORDER BY linha LIMIT 500",
                (rs, n) -> new ImportacaoDto.Erro(rs.getLong("linha"), rs.getString("mensagem")), id);
    }

    private static final RowMapper<ImportacaoDto> MAPPER = (rs, n) -> new ImportacaoDto(
            rs.getLong("id"),
            rs.getString("origem"),
            rs.getString("referencia"),
            rs.getString("status"),
            rs.getLong("linhas_lidas"),
            rs.getLong("linhas_importadas"),
            rs.getLong("linhas_com_erro"),
            rs.getString("mensagem"),
            rs.getObject("iniciada_em", java.time.OffsetDateTime.class),
            rs.getObject("concluida_em", java.time.OffsetDateTime.class));
}
