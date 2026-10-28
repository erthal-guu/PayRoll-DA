package br.com.payrollda.lrf.simam;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Faz, pela API, o mesmo caminho de quem usa a página "Relatórios da LRF" do SIMAM (TCE-PR):
 * abre o formulário ASP.NET, escolhe cada filtro (cada escolha é um postback que libera o próximo),
 * clica em Consultar e baixa o CSV pelo botão Exportar do ReportViewer da tela de visualização.
 *
 * Cada operação usa uma sessão própria (cookies) e um pedido por vez, sem paralelismo.
 */
@Component
public class SimamClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SimamClient.class);

    /** O SIMAM pode montar a página de outro jeito para clientes que não parecem um navegador. */
    private static final String NAVEGADOR = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    /** Tempo máximo de uma operação inteira (abrir + filtros + consultar + exportar). */
    private static final Duration LIMITE_TOTAL = Duration.ofSeconds(150);
    /** Tempo máximo de cada pedido ao SIMAM. */
    private static final Duration LIMITE_PEDIDO = Duration.ofSeconds(45);

    public record Opcao(String valor, String rotulo) {
    }

    public record Filtro(String chave, String id, String rotulo, boolean habilitado, String valor, List<Opcao> opcoes) {
    }

    /** O que o SIMAM devolveu depois do Consultar: a tela de visualização e, se deu, o CSV exportado. */
    public record Retorno(String htmlVisualizacao, String csv, String urlExportacao, Map<String, String> rotulos) {
    }

    private static final Pattern EXPORT_BASE = Pattern.compile("\"ExportUrlBase\":\"([^\"]+)\"");
    private static final Pattern BOTAO = Pattern.compile("WebForm_PostBackOptions\\(&quot;([^&]+)&quot;|WebForm_PostBackOptions\\(\"([^\"]+)\"");

    private final URI urlFormulario;
    private final Duration espera;
    private final java.nio.file.Path diagnostico;

    public SimamClient(@Value("${tce.simam.url:https://simam.tce.pr.gov.br/Paginas/Rel_LRF.aspx?relTipo=1}") String url,
                       @Value("${tce.simam.espera-ms:1500}") long esperaMs,
                       @Value("${tce.simam.diagnostico:${java.io.tmpdir}/simam-diagnostico}") String diagnostico) {
        this.urlFormulario = URI.create(url);
        this.espera = Duration.ofMillis(esperaMs);
        this.diagnostico = java.nio.file.Path.of(diagnostico);
    }

    /** Um pedido feito ao SIMAM, guardado para o diagnóstico. */
    record Passo(String etapa, String pedido, String corpo, int status, String resposta) {
    }

    private volatile List<Passo> ultimoRastro = List.of();

    private static final class Sessao {
        final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        Document pagina;
        URI url;
        final long prazo = System.nanoTime() + LIMITE_TOTAL.toNanos();
        String etapa = "abrindo o formulário";
        final List<Passo> rastro = new ArrayList<>();

        /** Tempo que ainda resta para o próximo pedido; falha com uma mensagem clara quando acabou. */
        Duration restante() throws java.net.http.HttpTimeoutException {
            long ns = prazo - System.nanoTime();
            if (ns <= 0) {
                throw new java.net.http.HttpTimeoutException("o SIMAM demorou mais de " + LIMITE_TOTAL.toSeconds()
                        + " segundos (etapa: " + etapa + ")");
            }
            Duration d = Duration.ofNanos(ns);
            return d.compareTo(LIMITE_PEDIDO) < 0 ? d : LIMITE_PEDIDO;
        }
    }

    /** Lê o estado dos filtros depois de aplicar as escolhas já feitas (para montar/atualizar o formulário). */
    public List<Filtro> filtros(Map<String, String> selecao) throws IOException, InterruptedException {
        Sessao s = abrir();
        ultimoRastro = s.rastro;
        try {
            aplicar(s, selecao);
        } finally {
            guardarUltima(s);
            ultimoRastro = s.rastro;
        }
        return lerFiltros(s.pagina);
    }

    public Retorno consultar(Map<String, String> selecao) throws IOException, InterruptedException {
        Sessao s = abrir();
        ultimoRastro = s.rastro;
        aplicar(s, selecao);
        for (Filtro f : lerFiltros(s.pagina)) {
            if (f.habilitado() && !f.opcoes().isEmpty() && (f.valor() == null || f.valor().isBlank())) {
                throw new IllegalArgumentException("Escolha o filtro \"" + f.rotulo() + "\" antes de consultar.");
            }
        }
        Map<String, String> rotulos = new LinkedHashMap<>();
        for (Filtro f : lerFiltros(s.pagina)) {
            f.opcoes().stream().filter(o -> o.valor().equals(f.valor())).findFirst().ifPresent(o -> rotulos.put(f.chave(), o.rotulo()));
        }
        Map<String, String> campos = campos(s.pagina);
        if (!clicarConsultar(s.pagina, campos)) {
            throw new IOException(diagnosticar(s, "não encontrei o botão Consultar na página do SIMAM (o layout pode ter mudado)"));
        }
        s.etapa = "clicando em Consultar (o SIMAM gera o relatório)";
        postar(s, campos);

        s.etapa = "esperando a tela do relatório";
        String html = s.pagina.outerHtml();
        String base = exportBase(html);
        ultimoRastro = s.rastro;
        for (int i = 0; base == null && i < 3; i++) { // a tela de visualização às vezes carrega o relatório depois
            Thread.sleep(espera.toMillis());
            s.pagina = Jsoup.parse(get(s, s.url), s.url.toString());
            html = s.pagina.outerHtml();
            base = exportBase(html);
        }
        if (base == null) {
            return new Retorno(html, null, null, rotulos);
        }
        URI exportacao = s.url.resolve(base + "CSV");
        s.etapa = "baixando o CSV do relatório";
        Thread.sleep(espera.toMillis());
        return new Retorno(html, get(s, exportacao), exportacao.toString(), rotulos);
    }

    private static final Pattern DO_POSTBACK = Pattern.compile("__doPostBack\\(\\s*['\"]([^'\"]+)['\"]");

    /**
     * Prepara os campos do clique em Consultar. O botão pode ser um link com postback (WebForm_PostBackOptions ou
     * __doPostBack) ou um input/button de envio; procura pelo id e, se não achar, pelo texto "Consultar".
     */
    private static boolean clicarConsultar(Document pagina, Map<String, String> campos) {
        Element botao = pagina.selectFirst("[id*=btnConsulta], [id*=btnConsultar], [name*=btnConsulta]");
        if (botao == null) {
            botao = pagina.select("a, button, input[type=submit], input[type=button]").stream()
                    .filter(e -> (e.text() + " " + e.attr("value")).toLowerCase(Locale.ROOT).contains("consultar"))
                    .findFirst().orElse(null);
        }
        if (botao == null) {
            return false;
        }
        String html = botao.outerHtml();
        Matcher m = BOTAO.matcher(html);
        Matcher p = DO_POSTBACK.matcher(html);
        if (m.find()) {
            campos.put("__EVENTTARGET", m.group(1) != null ? m.group(1) : m.group(2));
        } else if (p.find()) {
            campos.put("__EVENTTARGET", p.group(1));
        } else if (botao.hasAttr("name")) {
            campos.put("__EVENTTARGET", "");
            campos.put(botao.attr("name"), botao.hasAttr("value") ? botao.attr("value") : "Consultar");
        } else {
            return false;
        }
        campos.put("__EVENTARGUMENT", "");
        return true;
    }

    private static String exportBase(String html) {
        Matcher m = EXPORT_BASE.matcher(html);
        return m.find() ? m.group(1).replace("\\u0026", "&") : null;
    }

    private Sessao abrir() throws IOException, InterruptedException {
        Sessao s = new Sessao();
        s.url = urlFormulario;
        s.pagina = Jsoup.parse(get(s, urlFormulario), urlFormulario.toString());
        return s;
    }

    /**
     * Escolhe os filtros na ordem em que aparecem no formulário (cada um depende dos anteriores); cada select com
     * AutoPostBack dispara o postback do site, que pode mostrar campos novos. Depois de cada postback confere se o
     * SIMAM aceitou a escolha.
     */
    private void aplicar(Sessao s, Map<String, String> selecao) throws IOException, InterruptedException {
        if (selecao == null || selecao.isEmpty()) {
            return;
        }
        for (int passo = 0; passo < 20; passo++) {
            Element proximo = null;
            for (Element sel : selects(s.pagina)) {
                String valor = selecao.get(chaveDe(sel));
                if (valor == null || valor.isBlank()) {
                    if (sel.hasAttr("disabled") || semOpcoes(sel) || !valorAtual(sel).isEmpty()) {
                        continue; // campo não liberado, ou que já vem escolhido pelo SIMAM, não bloqueia os seguintes
                    }
                    break; // filtros seguintes dependem deste
                }
                if (!valor.equals(valorAtual(sel))) {
                    proximo = sel;
                    break;
                }
            }
            if (proximo == null) {
                break;
            }
            String chave = chaveDe(proximo);
            String valor = selecao.get(chave);
            if (proximo.hasAttr("disabled")) {
                throw new IOException(diagnosticar(s, "o SIMAM deixou o campo \"" + chave + "\" desabilitado"));
            }
            if (proximo.select("option").stream().noneMatch(o -> o.attr("value").equals(valor))) {
                throw new IllegalArgumentException("Opção inválida para o filtro \"" + chave + "\". Recarregue a página e escolha de novo.");
            }
            s.etapa = "escolhendo o campo \"" + chave + "\"";
            if (proximo.attr("onchange").contains("__doPostBack")) {
                Map<String, String> campos = campos(s.pagina);
                campos.put(proximo.attr("name"), valor);
                campos.put("__EVENTTARGET", proximo.attr("name"));
                campos.put("__EVENTARGUMENT", "");
                postar(s, campos);
                Element depois = select(s.pagina, chave);
                if (depois == null || !valor.equals(valorAtual(depois))) {
                    throw new IOException(diagnosticar(s, "o SIMAM não aceitou a escolha do campo \"" + chave + "\""));
                }
            } else {
                // sem postback: o valor só vale no envio final; guardamos na própria página
                proximo.select("option").forEach(o -> {
                    if (o.attr("value").equals(valor)) {
                        o.attr("selected", "selected");
                    } else {
                        o.removeAttr("selected");
                    }
                });
            }
            Thread.sleep(200); // um pedido por vez, sem martelar o servidor
        }
        for (Map.Entry<String, String> e : selecao.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank() && select(s.pagina, e.getKey()) == null) {
                throw new IOException(diagnosticar(s, "o campo \"" + e.getKey() + "\" não apareceu na página do SIMAM depois das escolhas anteriores"));
            }
        }
    }

    /** Guarda a última página do formulário em ultima-pagina.html, para analisar campos que o sistema não reconheceu. */
    private void guardarUltima(Sessao s) {
        try {
            java.nio.file.Files.createDirectories(diagnostico);
            java.nio.file.Files.writeString(diagnostico.resolve("ultima-pagina.html"), s.pagina.outerHtml());
        } catch (IOException | RuntimeException ignorado) {
            // diagnóstico é opcional
        }
    }

    /** Salva a página que o SIMAM devolveu para análise e monta a mensagem de erro com o caminho do arquivo. */
    private String diagnosticar(Sessao s, String motivo) {
        String arquivo = "";
        try {
            java.nio.file.Files.createDirectories(diagnostico);
            java.nio.file.Path p = diagnostico.resolve("filtros-" + System.currentTimeMillis() + ".html");
            java.nio.file.Files.writeString(p, s.pagina.outerHtml());
            arquivo = " A página recebida foi salva em " + p + ".";
        } catch (IOException | RuntimeException ignorado) {
            // sem diagnóstico, segue só com a mensagem
        }
        String titulo = s.pagina.title();
        return "Não consegui preencher o formulário: " + motivo
                + (titulo.isBlank() ? "" : " (página: \"" + titulo + "\")") + "." + arquivo;
    }

    /** Selects visíveis do formulário, na ordem da página. */
    private static List<Element> selects(Document pagina) {
        return pagina.select("form select").stream()
                .filter(sel -> !sel.attr("style").replace(" ", "").contains("display:none"))
                .filter(sel -> sel.parents().stream().noneMatch(p -> p.attr("style").replace(" ", "").contains("display:none")))
                .toList();
    }

    private static boolean semOpcoes(Element sel) {
        return sel.select("option").stream().allMatch(SimamClient::placeholder);
    }

    private static Element select(Document pagina, String chave) {
        for (Element sel : selects(pagina)) {
            if (chaveDe(sel).equals(chave)) {
                return sel;
            }
        }
        return null;
    }

    /** Chave do campo a partir do id, sem acento e sem o prefixo do controle (ddl, cbo, sel...). */
    private static String chaveDe(Element sel) {
        String id = sel.id().isBlank() ? sel.attr("name").replaceAll(".*\\$", "") : sel.id();
        String limpo = java.text.Normalizer.normalize(id, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String ultimo = limpo.replaceAll(".*[_$]", "").replaceFirst("^(ddl|cbo|cmb|sel|drp|lst|dd)", "");
        return ultimo.isBlank() ? limpo : ultimo;
    }

    private static String valorAtual(Element sel) {
        Element o = sel.selectFirst("option[selected]");
        if (o == null) {
            o = sel.selectFirst("option");
        }
        return o == null || placeholder(o) ? "" : o.attr("value");
    }

    /** Opção do tipo "Selecione..." (valor vazio, 0 ou -1 com texto de instrução), que não é uma escolha de verdade. */
    private static boolean placeholder(Element o) {
        String v = o.attr("value").trim();
        String t = o.text().trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() || ((v.equals("0") || v.equals("-1")) && (t.startsWith("selecione") || t.startsWith("--") || t.isEmpty()));
    }

    private static List<Filtro> lerFiltros(Document pagina) {
        List<Filtro> lista = new ArrayList<>();
        for (Element sel : selects(pagina)) {
            String chave = chaveDe(sel);
            Element lbl = null;
            if (!sel.id().isBlank()) {
                lbl = pagina.selectFirst("label[for=" + sel.id() + "]");
                if (lbl == null && sel.id().contains("ddl")) {
                    lbl = pagina.getElementById(sel.id().replace("ddl", "lbl"));
                }
            }
            String rotulo = lbl != null && !lbl.text().isBlank() ? lbl.text().trim() : Character.toUpperCase(chave.charAt(0)) + chave.substring(1);
            List<Opcao> opcoes = sel.select("option").stream()
                    .filter(o -> !placeholder(o))
                    .map(o -> new Opcao(o.attr("value"), o.text().trim())).toList();
            lista.add(new Filtro(chave, sel.id(), rotulo, !sel.hasAttr("disabled"), valorAtual(sel), opcoes));
        }
        return lista;
    }

    private static Map<String, String> campos(Document pagina) {
        FormElement form = (FormElement) pagina.selectFirst("form[id=form2]") ;
        if (form == null) {
            form = pagina.select("form").stream().filter(f -> f instanceof FormElement).map(f -> (FormElement) f).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Formulário não encontrado na página do SIMAM."));
        }
        // botões não entram: no ASP.NET, mandar o nome de um botão é o mesmo que clicar nele
        java.util.Set<String> botoes = new java.util.HashSet<>();
        form.select("input[type=submit], input[type=image], input[type=button], button").forEach(b -> botoes.add(b.attr("name")));
        Map<String, String> m = new LinkedHashMap<>();
        for (Connection.KeyVal kv : form.formData()) {
            if (!botoes.contains(kv.key())) {
                m.put(kv.key(), kv.value());
            }
        }
        return m;
    }

    private void postar(Sessao s, Map<String, String> campos) throws IOException, InterruptedException {
        StringBuilder corpo = new StringBuilder();
        campos.forEach((k, v) -> corpo.append(corpo.length() > 0 ? "&" : "")
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        CORPO_ATUAL.set(resumirCorpo(campos));
        HttpRequest req = HttpRequest.newBuilder(s.url).timeout(s.restante())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", s.url.toString())
                .header("User-Agent", NAVEGADOR)
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())).build();
        HttpResponse<String> r = enviar(s, req, CORPO_ATUAL.get());
        if (r.statusCode() >= 400) {
            throw new IOException("O SIMAM respondeu HTTP " + r.statusCode() + ".");
        }
        s.url = r.uri();
        s.pagina = Jsoup.parse(r.body(), r.uri().toString());
    }

    private static final ThreadLocal<String> CORPO_ATUAL = new ThreadLocal<>();

    private static HttpResponse<String> enviar(Sessao s, HttpRequest req, String corpo) throws IOException, InterruptedException {
        CORPO_ATUAL.remove();
        long inicio = System.nanoTime();
        log.info("SIMAM: {} ({} {})", s.etapa, req.method(), req.uri().getPath());
        try {
            HttpResponse<String> r = s.http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("SIMAM: respondeu {} em {} ms ({} caracteres, endereço final {})", r.statusCode(),
                    (System.nanoTime() - inicio) / 1_000_000, r.body().length(), r.uri());
            s.rastro.add(new Passo(s.etapa, req.method() + " " + req.uri() + " -> " + r.uri(), corpo, r.statusCode(), r.body()));
            return r;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new java.net.http.HttpTimeoutException("o SIMAM não respondeu a tempo (etapa: " + s.etapa + ")");
        }
    }

    /** Campos enviados, sem o conteúdo enorme do __VIEWSTATE/__EVENTVALIDATION. */
    private static String resumirCorpo(Map<String, String> campos) {
        StringBuilder b = new StringBuilder();
        campos.forEach((k, v) -> b.append(k).append(" = ")
                .append(v != null && v.length() > 80 ? v.substring(0, 40) + "… (" + v.length() + " caracteres)" : v).append('\n'));
        return b.toString();
    }

    /** Zip com cada página que o SIMAM devolveu na última operação e um resumo dos pedidos. */
    public byte[] diagnosticoZip() throws IOException {
        var saida = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            StringBuilder resumo = new StringBuilder();
            int i = 1;
            for (Passo p : ultimoRastro) {
                String nome = String.format("%02d-pagina.html", i);
                resumo.append(String.format("#%02d %s%n%s%nHTTP %d, %d caracteres -> %s%n", i, p.etapa(), p.pedido(),
                        p.status(), p.resposta().length(), nome));
                if (p.corpo() != null) {
                    resumo.append("Campos enviados:\n").append(p.corpo());
                }
                resumo.append('\n');
                zip.putNextEntry(new java.util.zip.ZipEntry(nome));
                zip.write(p.resposta().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                i++;
            }
            zip.putNextEntry(new java.util.zip.ZipEntry("resumo.txt"));
            zip.write((resumo.isEmpty() ? "Nenhuma consulta ao SIMAM feita desde que o backend subiu.\n" : resumo.toString())
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return saida.toByteArray();
    }

    private String get(Sessao s, URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(s.restante())
                .header("User-Agent", NAVEGADOR)
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .header("Referer", s.url == null ? uri.toString() : s.url.toString()).GET().build();
        HttpResponse<String> r = enviar(s, req, CORPO_ATUAL.get());
        if (r.statusCode() >= 400) {
            throw new IOException("O SIMAM respondeu HTTP " + r.statusCode() + " em " + uri.getPath() + ".");
        }
        return r.body();
    }
}
