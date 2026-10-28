package br.com.payrollda.lrf.dashboard;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.payrollda.lrf.auth.UsuarioAtual;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Composicao;
import br.com.payrollda.lrf.dashboard.DashboardDtos.EntidadeLinha;
import br.com.payrollda.lrf.dashboard.DashboardDtos.FaixasNoPeriodo;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Municipio;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Pagina;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Periodo;
import br.com.payrollda.lrf.dashboard.DashboardDtos.PontoEvolucao;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Resumo;

@Repository
public class DashboardRepository {

    private static final String FROM = " FROM despesa_pessoal d JOIN entidade e ON e.id = d.entidade_id";
    private static final Map<String, String> ORDENS = Map.of(
            "usoLimite", "(d.percentual / d.limite_maximo)",
            "percentual", "d.percentual",
            "despesaTotal", "d.despesa_total",
            "entidade", "e.nome",
            "municipio", "e.municipio");

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Periodo> periodos() {
        return jdbc.query("SELECT DISTINCT ano, mes FROM despesa_pessoal ORDER BY ano DESC, mes DESC",
                (rs, n) -> new Periodo(rs.getInt("ano"), rs.getInt("mes")));
    }

    public Optional<Periodo> ultimoPeriodo() {
        return periodos().stream().findFirst();
    }

    public List<Municipio> municipios() {
        String restrito = UsuarioAtual.codIbgeRestrito();
        return jdbc.query("SELECT DISTINCT cod_ibge, municipio FROM entidade"
                        + (restrito != null ? " WHERE cod_ibge = :c" : "") + " ORDER BY municipio",
                new MapSqlParameterSource("c", restrito),
                (rs, n) -> new Municipio(rs.getString("cod_ibge"), rs.getString("municipio")));
    }

    public Resumo resumo(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = f.where(p, true, true);
        Map<String, Object> totais = jdbc.queryForMap("""
                SELECT count(*) AS entidades,
                       coalesce(sum(d.despesa_total), 0) AS despesa_total,
                       coalesce(sum(d.rcl_ajustada), 0) AS rcl_total,
                       coalesce(round(avg(d.percentual * 100 / d.limite_maximo), 2), 0) AS uso_medio_limite,
                       coalesce(sum(d.pessoal_ativo), 0) AS ativo,
                       coalesce(sum(d.inativos_pensionistas), 0) AS inativos,
                       coalesce(sum(d.outras_despesas), 0) AS outras,
                       coalesce(sum(d.nao_computadas), 0) AS nao_computadas
                """ + FROM + where, p);

        Map<String, Long> faixas = faixasVazias();
        jdbc.query("SELECT " + Filtro.SQL_FAIXA + " AS faixa, count(*) AS qtd" + FROM + where + " GROUP BY 1", p,
                rs -> {
                    faixas.put(rs.getString("faixa"), rs.getLong("qtd"));
                });

        return new Resumo(
                new Periodo(f.ano(), f.mes()),
                ((Number) totais.get("entidades")).longValue(),
                (BigDecimal) totais.get("despesa_total"),
                (BigDecimal) totais.get("rcl_total"),
                (BigDecimal) totais.get("uso_medio_limite"),
                faixas,
                new Composicao((BigDecimal) totais.get("ativo"), (BigDecimal) totais.get("inativos"),
                        (BigDecimal) totais.get("outras"), (BigDecimal) totais.get("nao_computadas")));
    }

    public Pagina<EntidadeLinha> entidades(Filtro f, String ordem, String direcao, int pagina, int tamanho) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = f.where(p, true, true);
        String coluna = ORDENS.getOrDefault(ordem, ORDENS.get("usoLimite"));
        String dir = "asc".equalsIgnoreCase(direcao) ? "ASC" : "DESC";
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, p, Long.class);
        p.addValue("limite", tamanho).addValue("offset", (long) pagina * tamanho);
        List<EntidadeLinha> itens = jdbc.query("""
                SELECT e.id, e.cod_ibge, e.municipio, e.nome, e.poder, d.despesa_total, d.rcl_ajustada,
                       d.percentual, d.limite_maximo, """ + Filtro.SQL_FAIXA + " AS faixa"
                + FROM + where + " ORDER BY " + coluna + " " + dir + ", e.nome LIMIT :limite OFFSET :offset", p,
                (rs, n) -> new EntidadeLinha(rs.getLong("id"), rs.getString("cod_ibge"), rs.getString("municipio"),
                        rs.getString("nome"), rs.getString("poder"), rs.getBigDecimal("despesa_total"),
                        rs.getBigDecimal("rcl_ajustada"), rs.getBigDecimal("percentual"),
                        rs.getBigDecimal("limite_maximo"), rs.getString("faixa")));
        return new Pagina<>(itens, total, pagina, tamanho);
    }

    public List<DashboardDtos.EntidadeResumo> entidadesDoMunicipio(String codIbge) {
        String restrito = UsuarioAtual.codIbgeRestrito();
        if (restrito != null && !restrito.equals(codIbge)) {
            return List.of();
        }
        return jdbc.query("SELECT id, nome, poder FROM entidade WHERE cod_ibge = :c ORDER BY poder, nome",
                new MapSqlParameterSource("c", codIbge),
                (rs, n) -> new DashboardDtos.EntidadeResumo(rs.getLong("id"), rs.getString("nome"), rs.getString("poder")));
    }

    /** Todas as linhas do demonstrativo de uma entidade num período (Optional vazio se não houver dado). */
    public Optional<DashboardDtos.Demonstrativo> demonstrativo(long entidadeId, int ano, int mes) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", entidadeId).addValue("ano", ano).addValue("mes", mes);
        String restrito = UsuarioAtual.codIbgeRestrito();
        p.addValue("c", restrito);
        return jdbc.query("""
                SELECT e.id, e.cod_ibge, e.municipio, e.nome, e.poder, d.ano, d.mes, d.despesa_bruta, d.pessoal_ativo,
                       d.inativos_pensionistas, d.outras_despesas, d.nao_computadas, d.despesa_total, d.rcl_ajustada,
                       d.percentual, d.limite_maximo, """ + Filtro.SQL_FAIXA + " AS faixa" + FROM
                + " WHERE e.id = :id AND d.ano = :ano AND d.mes = :mes" + (restrito != null ? " AND e.cod_ibge = :c" : ""), p,
                (rs, n) -> new DashboardDtos.Demonstrativo(rs.getLong("id"), rs.getString("cod_ibge"),
                        rs.getString("municipio"), rs.getString("nome"), rs.getString("poder"), rs.getInt("ano"),
                        rs.getInt("mes"), rs.getBigDecimal("despesa_bruta"), rs.getBigDecimal("pessoal_ativo"),
                        rs.getBigDecimal("inativos_pensionistas"), rs.getBigDecimal("outras_despesas"),
                        rs.getBigDecimal("nao_computadas"), rs.getBigDecimal("despesa_total"),
                        rs.getBigDecimal("rcl_ajustada"), rs.getBigDecimal("percentual"),
                        rs.getBigDecimal("limite_maximo"), rs.getString("faixa")))
                .stream().findFirst();
    }

    public List<PontoEvolucao> evolucaoEntidade(long entidadeId) {
        String restrito = UsuarioAtual.codIbgeRestrito();
        return jdbc.query("""
                SELECT ano, mes, despesa_total, rcl_ajustada, percentual, limite_maximo
                  FROM despesa_pessoal WHERE entidade_id = :id"""
                        + (restrito != null ? " AND entidade_id IN (SELECT id FROM entidade WHERE cod_ibge = :c)" : "")
                        + " ORDER BY ano, mes",
                new MapSqlParameterSource("id", entidadeId).addValue("c", restrito),
                (rs, n) -> new PontoEvolucao(rs.getInt("ano"), rs.getInt("mes"), rs.getBigDecimal("despesa_total"),
                        rs.getBigDecimal("rcl_ajustada"), rs.getBigDecimal("percentual"),
                        rs.getBigDecimal("limite_maximo")));
    }

    /** Quantas entidades em cada faixa, período a período (respeita poder, município e busca). */
    public List<FaixasNoPeriodo> evolucaoFaixas(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = f.where(p, false, false);
        Map<String, Map<String, Long>> porPeriodo = new TreeMap<>();
        jdbc.query("SELECT d.ano, d.mes, " + Filtro.SQL_FAIXA + " AS faixa, count(*) AS qtd" + FROM + where
                + " GROUP BY 1, 2, 3", p, rs -> {
                    String chave = String.format("%04d-%02d", rs.getInt("ano"), rs.getInt("mes"));
                    porPeriodo.computeIfAbsent(chave, k -> faixasVazias()).put(rs.getString("faixa"), rs.getLong("qtd"));
                });
        return porPeriodo.entrySet().stream()
                .map(e -> new FaixasNoPeriodo(Integer.parseInt(e.getKey().substring(0, 4)),
                        Integer.parseInt(e.getKey().substring(5)), e.getValue()))
                .toList();
    }

    private static Map<String, Long> faixasVazias() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String f : List.of("REGULAR", "ALERTA", "PRUDENCIAL", "ACIMA_LIMITE")) {
            m.put(f, 0L);
        }
        return m;
    }
}
