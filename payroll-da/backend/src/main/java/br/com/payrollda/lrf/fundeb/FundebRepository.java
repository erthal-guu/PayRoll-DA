package br.com.payrollda.lrf.fundeb;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.payrollda.lrf.simam.RelatorioMdeParser.Fundeb;

/** Transferências do FUNDEB por município e ano (RREO MDE do SIMAM). */
@Repository
public class FundebRepository {

    /** Um ano do município. transferido é o valor das "Transferências do FUNDEB" do relatório de análise. */
    public record Ano(String codIbge, String municipio, int ano, String periodo, int mesFinal, BigDecimal transferido,
                      BigDecimal receitasDestinadas, BigDecimal receitasRecebidas, BigDecimal complementacaoUniao,
                      BigDecimal resultadoLiquido, BigDecimal despesasProfissionais, BigDecimal totalDespesas,
                      BigDecimal percentualProfissionais) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FundebRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o período do ano; um período anterior ao que já está gravado não substitui o mais recente. */
    public void salvar(String codIbge, String municipio, int ano, String periodo, int mesFinal, Fundeb f) {
        var p = new MapSqlParameterSource().addValue("c", codIbge).addValue("m", municipio).addValue("a", ano)
                .addValue("p", periodo).addValue("mf", mesFinal)
                .addValue("rd", f.receitasDestinadas()).addValue("rr", f.receitasRecebidas()).addValue("t", f.transferencias())
                .addValue("cu", f.complementacaoUniao()).addValue("rl", f.resultadoLiquido())
                .addValue("dp", f.despesasProfissionais()).addValue("td", f.totalDespesas()).addValue("pp", f.percentualProfissionais());
        jdbc.update("""
                INSERT INTO fundeb_municipio (cod_ibge, municipio, ano, periodo, mes_final, receitas_destinadas, receitas_recebidas,
                       transferencias, complementacao_uniao, resultado_liquido, despesas_profissionais, total_despesas, perc_profissionais)
                VALUES (:c, :m, :a, :p, :mf, :rd, :rr, :t, :cu, :rl, :dp, :td, :pp)
                ON CONFLICT (cod_ibge, ano) DO UPDATE SET municipio = EXCLUDED.municipio, periodo = EXCLUDED.periodo,
                       mes_final = EXCLUDED.mes_final, receitas_destinadas = EXCLUDED.receitas_destinadas,
                       receitas_recebidas = EXCLUDED.receitas_recebidas, transferencias = EXCLUDED.transferencias,
                       complementacao_uniao = EXCLUDED.complementacao_uniao, resultado_liquido = EXCLUDED.resultado_liquido,
                       despesas_profissionais = EXCLUDED.despesas_profissionais, total_despesas = EXCLUDED.total_despesas,
                       perc_profissionais = EXCLUDED.perc_profissionais, importado_em = now()
                 WHERE fundeb_municipio.mes_final <= EXCLUDED.mes_final""", p);
    }

    public List<Ano> anos(String codIbge) {
        return jdbc.query("SELECT * FROM fundeb_municipio WHERE cod_ibge = :c ORDER BY ano",
                new MapSqlParameterSource("c", codIbge), (rs, i) -> {
                    BigDecimal t = rs.getBigDecimal("transferencias");
                    BigDecimal rr = rs.getBigDecimal("receitas_recebidas");
                    return new Ano(rs.getString("cod_ibge"), rs.getString("municipio"), rs.getInt("ano"), rs.getString("periodo"),
                            rs.getInt("mes_final"), t != null ? t : rr, rs.getBigDecimal("receitas_destinadas"), rr,
                            rs.getBigDecimal("complementacao_uniao"), rs.getBigDecimal("resultado_liquido"),
                            rs.getBigDecimal("despesas_profissionais"), rs.getBigDecimal("total_despesas"),
                            rs.getBigDecimal("perc_profissionais"));
                });
    }
}
