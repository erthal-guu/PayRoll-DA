package br.com.payrollda.lrf.dashboard;

import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import br.com.payrollda.lrf.auth.UsuarioAtual;

/**
 * Filtros comuns do dashboard. Gera o trecho WHERE com parâmetros nomeados
 * (nunca concatena valores do usuário no SQL).
 */
public record Filtro(Integer ano, Integer mes, String poder, String codIbge, String faixa, String busca) {

    static final Set<String> PODERES = Set.of("EXECUTIVO", "LEGISLATIVO");
    static final Set<String> FAIXAS = Set.of("REGULAR", "ALERTA", "PRUDENCIAL", "ACIMA_LIMITE");

    /**
     * Faixas da LRF sobre o limite máximo: alerta a partir de 90% (art. 59, §1º, II),
     * prudencial a partir de 95% (art. 22, parágrafo único).
     */
    static final String SQL_FAIXA = """
            CASE WHEN d.percentual >= d.limite_maximo THEN 'ACIMA_LIMITE'
                 WHEN d.percentual >= d.limite_maximo * 0.95 THEN 'PRUDENCIAL'
                 WHEN d.percentual >= d.limite_maximo * 0.90 THEN 'ALERTA'
                 ELSE 'REGULAR' END""";

    public Filtro {
        poder = normalizarEnum(poder, PODERES, "poder");
        faixa = normalizarEnum(faixa, FAIXAS, "faixa");
        codIbge = codIbge == null || codIbge.isBlank() ? null : codIbge.trim();
        // usuário de prefeitura só enxerga o próprio município, qualquer que seja o filtro pedido
        String restrito = UsuarioAtual.codIbgeRestrito();
        if (restrito != null) {
            codIbge = restrito;
        }
        busca = busca == null || busca.isBlank() ? null : busca.trim();
        if (mes != null && (mes < 1 || mes > 12)) {
            throw new IllegalArgumentException("mes deve estar entre 1 e 12");
        }
    }

    Filtro comPeriodo(int novoAno, int novoMes) {
        return new Filtro(novoAno, novoMes, poder, codIbge, faixa, busca);
    }

    String where(MapSqlParameterSource p, boolean incluirPeriodo, boolean incluirFaixa) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (incluirPeriodo) {
            sb.append(" AND d.ano = :ano AND d.mes = :mes");
            p.addValue("ano", ano).addValue("mes", mes);
        }
        if (poder != null) {
            sb.append(" AND e.poder = :poder");
            p.addValue("poder", poder);
        }
        if (codIbge != null) {
            sb.append(" AND e.cod_ibge = :codIbge");
            p.addValue("codIbge", codIbge);
        }
        if (incluirFaixa && faixa != null) {
            sb.append(" AND ").append(SQL_FAIXA).append(" = :faixa");
            p.addValue("faixa", faixa);
        }
        if (busca != null) {
            sb.append(" AND (unaccent_lower(e.nome) LIKE :busca OR unaccent_lower(e.municipio) LIKE :busca)");
            p.addValue("busca", "%" + semAcento(busca) + "%");
        }
        return sb.toString();
    }

    private static String normalizarEnum(String v, Set<String> validos, String nome) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String up = v.trim().toUpperCase(Locale.ROOT);
        if (!validos.contains(up)) {
            throw new IllegalArgumentException(nome + " inválido: " + v + ". Valores aceitos: " + validos);
        }
        return up;
    }

    static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
