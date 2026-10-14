package br.com.payrollda.lrf.importacao;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import br.com.payrollda.lrf.config.ImportacaoProperties;

/**
 * Baixa o arquivo de dados abertos do TCE-PR para um arquivo temporário.
 * Se vier compactado (.zip), extrai o primeiro CSV.
 *
 * Só aceita HTTPS e domínios listados em tce.importacao.dominios-permitidos,
 * para a API não virar um "baixador" de qualquer endereço.
 */
@Component
public class TceDownloadClient {

    private final ImportacaoProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public TceDownloadClient(ImportacaoProperties props) {
        this.props = props;
    }

    public URI validar(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL inválida: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Use um endereço https completo, por exemplo https://servicos.tce.pr.gov.br/...");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean permitido = props.dominiosPermitidos().stream()
                .anyMatch(d -> host.equals(d) || host.endsWith("." + d));
        if (!permitido) {
            throw new IllegalArgumentException("Domínio " + host + " não está liberado. Domínios aceitos: "
                    + String.join(", ", props.dominiosPermitidos()));
        }
        return uri;
    }

    /** @return caminho de um CSV temporário; quem chama deve apagá-lo */
    public Path baixarCsv(URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "payroll-da/0.1 (importacao de dados abertos)")
                .GET().build();
        Path bruto = Files.createTempFile("tce-", ".download");
        HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(bruto));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(bruto);
            throw new IOException("O TCE-PR respondeu HTTP " + resp.statusCode() + " para " + uri);
        }
        if (!ehZip(bruto)) {
            return bruto;
        }
        try {
            return extrairPrimeiroCsv(bruto);
        } finally {
            Files.deleteIfExists(bruto);
        }
    }

    static boolean ehZip(Path arquivo) throws IOException {
        try (InputStream in = Files.newInputStream(arquivo)) {
            byte[] assinatura = in.readNBytes(2);
            return assinatura.length == 2 && assinatura[0] == 'P' && assinatura[1] == 'K';
        }
    }

    static Path extrairPrimeiroCsv(Path zip) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    Path csv = Files.createTempFile("tce-", ".csv");
                    Files.copy(zin, csv, StandardCopyOption.REPLACE_EXISTING);
                    return csv;
                }
            }
        }
        throw new IOException("O arquivo .zip baixado não contém nenhum .csv");
    }
}
