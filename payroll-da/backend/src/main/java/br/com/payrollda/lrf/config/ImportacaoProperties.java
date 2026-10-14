package br.com.payrollda.lrf.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros da importação, lidos de {@code tce.importacao} no application.yml.
 *
 * @param colunas mapa "campo lógico" -> "nome da coluna no CSV"
 */
@ConfigurationProperties(prefix = "tce.importacao")
public record ImportacaoProperties(
        String urlPadrao,
        String urlConsulta,
        List<String> dominiosPermitidos,
        String charset,
        String delimitador,
        int tamanhoLote,
        int maxErrosRegistrados,
        Map<String, String> colunas) {

    public ImportacaoProperties {
        dominiosPermitidos = dominiosPermitidos == null ? List.of() : List.copyOf(dominiosPermitidos);
        charset = charset == null || charset.isBlank() ? "ISO-8859-1" : charset;
        delimitador = delimitador == null || delimitador.isEmpty() ? ";" : delimitador;
        tamanhoLote = tamanhoLote <= 0 ? 1000 : tamanhoLote;
        maxErrosRegistrados = maxErrosRegistrados <= 0 ? 200 : maxErrosRegistrados;
        colunas = colunas == null ? Map.of() : Map.copyOf(colunas);
    }
}
