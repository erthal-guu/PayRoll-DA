package br.com.payrollda.lrf.importacao;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.payrollda.lrf.config.ImportacaoProperties;

/**
 * Orquestra uma importação: recebe o arquivo (upload ou download do TCE-PR),
 * registra a importação e processa em segundo plano, gravando em lotes.
 */
@Service
public class ImportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoService.class);

    private final ImportacaoRepository repo;
    private final CsvDespesaParser parser;
    private final TceDownloadClient download;
    private final ImportacaoProperties props;
    private final Executor executor;

    public ImportacaoService(ImportacaoRepository repo, CsvDespesaParser parser, TceDownloadClient download,
                             ImportacaoProperties props, @Qualifier("importacaoExecutor") Executor executor) {
        this.repo = repo;
        this.parser = parser;
        this.download = download;
        this.props = props;
        this.executor = executor;
    }

    public ImportacaoDto iniciarUpload(MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }
        Path temp = Files.createTempFile("upload-", ".csv");
        arquivo.transferTo(temp);
        long id = repo.criar("UPLOAD", nomeSeguro(arquivo.getOriginalFilename()));
        executor.execute(() -> processarArquivo(id, temp));
        return repo.buscar(id).orElseThrow();
    }

    public ImportacaoDto iniciarDownload(String url) {
        String alvo = (url == null || url.isBlank()) ? props.urlPadrao() : url;
        if (alvo == null || alvo.isBlank()) {
            throw new IllegalArgumentException("Informe a URL do arquivo de dados abertos do TCE-PR "
                    + "ou configure tce.importacao.url-padrao.");
        }
        URI uri = download.validar(alvo);
        long id = repo.criar("URL", uri.toString());
        executor.execute(() -> {
            try {
                Path csv = download.baixarCsv(uri);
                processarArquivo(id, csv);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                repo.finalizar(id, "FALHOU", 0, 0, 0, "Download interrompido.");
            } catch (Exception e) {
                log.warn("Falha no download da importação {}", id, e);
                repo.finalizar(id, "FALHOU", 0, 0, 0, "Não foi possível baixar o arquivo: " + e.getMessage());
            }
        });
        return repo.buscar(id).orElseThrow();
    }

    /**
     * Usado pelo botão "Consultar": baixa e importa na mesma requisição,
     * para a tela já mostrar o resultado e liberar o Excel.
     */
    public ImportacaoDto importarAgora(String url) {
        URI uri = download.validar(url);
        long id = repo.criar("URL", uri.toString());
        try {
            processarArquivo(id, download.baixarCsv(uri));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            repo.finalizar(id, "FALHOU", 0, 0, 0, "Download interrompido.");
        } catch (Exception e) {
            log.warn("Falha no download da consulta {}", id, e);
            repo.finalizar(id, "FALHOU", 0, 0, 0, "Não foi possível baixar o arquivo do TCE-PR: " + e.getMessage());
        }
        return repo.buscar(id).orElseThrow();
    }

    void processarArquivo(long id, Path csv) {
        Contador c = new Contador();
        List<LinhaDespesa> lote = new ArrayList<>(props.tamanhoLote());
        try (Reader reader = Files.newBufferedReader(csv, Charset.forName(props.charset()))) {
            repo.limparCache();
            parser.ler(reader, new CsvDespesaParser.Ouvinte() {
                @Override
                public void linhaValida(LinhaDespesa linha) {
                    c.lidas++;
                    lote.add(linha);
                    if (lote.size() >= props.tamanhoLote()) {
                        gravar(id, lote, c);
                    }
                }

                @Override
                public void linhaInvalida(long numeroLinha, String motivo) {
                    c.lidas++;
                    c.erros++;
                    if (c.erros <= props.maxErrosRegistrados()) {
                        repo.registrarErro(id, numeroLinha, motivo);
                    }
                }
            });
            gravar(id, lote, c);
            String status = c.erros == 0 ? "CONCLUIDA" : "COM_ERROS";
            String msg = c.importadas + " registros importados" + (c.erros > 0 ? ", " + c.erros + " linhas com erro" : "") + ".";
            repo.finalizar(id, status, c.lidas, c.importadas, c.erros, msg);
        } catch (CsvDespesaParser.FormatoInvalidoException e) {
            repo.finalizar(id, "FALHOU", c.lidas, c.importadas, c.erros, e.getMessage());
        } catch (Exception e) {
            log.error("Falha na importação {}", id, e);
            repo.finalizar(id, "FALHOU", c.lidas, c.importadas, c.erros, "Erro ao processar o arquivo: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(csv);
            } catch (IOException ignored) {
                // arquivo temporário; o sistema operacional limpa depois
            }
        }
    }

    private void gravar(long id, List<LinhaDespesa> lote, Contador c) {
        if (lote.isEmpty()) {
            return;
        }
        repo.gravarLote(id, lote);
        c.importadas += lote.size();
        lote.clear();
        repo.atualizarProgresso(id, c.lidas, c.importadas, c.erros);
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "arquivo.csv";
        }
        String base = Path.of(nome).getFileName().toString();
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    private static final class Contador {
        long lidas;
        long importadas;
        long erros;
    }
}
