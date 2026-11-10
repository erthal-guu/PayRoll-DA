package br.com.payrollda.lrf.professores;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.payrollda.lrf.professores.ProfessoresDtos.Nivel;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Piso;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Plano;
import br.com.payrollda.lrf.professores.ProfessoresDtos.PlanoResumo;

@Repository
public class ProfessoresRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfessoresRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Piso> pisos() {
        return jdbc.query("SELECT ano, valor_20h, ato_legal FROM piso_magisterio ORDER BY ano DESC",
                (rs, n) -> new Piso(rs.getInt("ano"), rs.getBigDecimal("valor_20h"), rs.getString("ato_legal")));
    }

    public Optional<Piso> piso(int ano) {
        return jdbc.query("SELECT ano, valor_20h, ato_legal FROM piso_magisterio WHERE ano = :a",
                new MapSqlParameterSource("a", ano),
                (rs, n) -> new Piso(rs.getInt("ano"), rs.getBigDecimal("valor_20h"), rs.getString("ato_legal"))).stream().findFirst();
    }

    public void salvarPiso(Piso p) {
        jdbc.update("INSERT INTO piso_magisterio (ano, valor_20h, ato_legal) VALUES (:a, :v, :l) "
                        + "ON CONFLICT (ano) DO UPDATE SET valor_20h = EXCLUDED.valor_20h, ato_legal = EXCLUDED.ato_legal, atualizado_em = now()",
                new MapSqlParameterSource("a", p.ano()).addValue("v", p.valor20h()).addValue("l", p.atoLegal()));
    }

    /** Planos cadastrados; restrito != null limita ao município do usuário. */
    public List<PlanoResumo> planos(String restrito) {
        return jdbc.query("SELECT cod_ibge, municipio, ano, carga_horaria FROM plano_magisterio"
                        + (restrito != null ? " WHERE cod_ibge = :c" : "") + " ORDER BY municipio, ano DESC, carga_horaria",
                new MapSqlParameterSource("c", restrito),
                (rs, n) -> new PlanoResumo(rs.getString("cod_ibge"), rs.getString("municipio"), rs.getInt("ano"), rs.getInt("carga_horaria")));
    }

    public Optional<Plano> plano(String codIbge, int ano, int carga) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", codIbge).addValue("a", ano).addValue("h", carga);
        List<Long> ids = jdbc.queryForList("SELECT id FROM plano_magisterio WHERE cod_ibge = :c AND ano = :a AND carga_horaria = :h", p, Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<Nivel> niveis = jdbc.query("SELECT nome, perc_sobre_base FROM plano_magisterio_nivel WHERE plano_id = :id ORDER BY ordem",
                new MapSqlParameterSource("id", ids.get(0)), (rs, n) -> new Nivel(rs.getString("nome"), rs.getBigDecimal("perc_sobre_base")));
        return jdbc.query("SELECT * FROM plano_magisterio WHERE id = :id", new MapSqlParameterSource("id", ids.get(0)),
                (rs, n) -> new Plano(rs.getString("cod_ibge"), rs.getString("municipio"), rs.getInt("ano"), rs.getInt("carga_horaria"),
                        rs.getBigDecimal("vencimento_base"), rs.getBigDecimal("perc_classe"), rs.getInt("classe_final"),
                        rs.getString("lei"), niveis)).stream().findFirst();
    }

    /** Grava o plano e troca os níveis (o plano é sempre salvo inteiro pela tela). */
    @Transactional
    public void salvarPlano(Plano p) {
        MapSqlParameterSource m = new MapSqlParameterSource("c", p.codIbge()).addValue("m", p.municipio()).addValue("a", p.ano())
                .addValue("h", p.cargaHoraria()).addValue("b", p.vencimentoBase()).addValue("pc", p.percClasse())
                .addValue("cf", p.classeFinal()).addValue("l", p.lei());
        Long id = jdbc.queryForObject("INSERT INTO plano_magisterio (cod_ibge, municipio, ano, carga_horaria, vencimento_base, perc_classe, classe_final, lei) "
                + "VALUES (:c, :m, :a, :h, :b, :pc, :cf, :l) ON CONFLICT (cod_ibge, ano, carga_horaria) DO UPDATE SET "
                + "municipio = EXCLUDED.municipio, vencimento_base = EXCLUDED.vencimento_base, perc_classe = EXCLUDED.perc_classe, "
                + "classe_final = EXCLUDED.classe_final, lei = EXCLUDED.lei, atualizado_em = now() RETURNING id", m, Long.class);
        jdbc.update("DELETE FROM plano_magisterio_nivel WHERE plano_id = :id", new MapSqlParameterSource("id", id));
        for (int i = 0; i < p.niveis().size(); i++) {
            Nivel n = p.niveis().get(i);
            jdbc.update("INSERT INTO plano_magisterio_nivel (plano_id, ordem, nome, perc_sobre_base) VALUES (:id, :o, :n, :p)",
                    new MapSqlParameterSource("id", id).addValue("o", i + 1).addValue("n", n.nome()).addValue("p", n.percSobreBase()));
        }
    }

    public void removerPlano(String codIbge, int ano, int carga) {
        jdbc.update("DELETE FROM plano_magisterio WHERE cod_ibge = :c AND ano = :a AND carga_horaria = :h",
                new MapSqlParameterSource("c", codIbge).addValue("a", ano).addValue("h", carga));
    }
}
