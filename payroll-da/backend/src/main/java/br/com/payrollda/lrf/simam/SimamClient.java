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

    /** Ordem em que os filtros dependem uns dos outros no formulário. */
    static final List<String> ORDEM = List.of("tipo", "municipio", "entidade", "relatorio", "ano", "periodo");

    public record Opcao(String valor, String rotulo) {
    }

    public record Filtro(String chave, String id, String rotulo, boolean habilitado, String valor, List<Opcao> opcoes) {
    }

    /** O que o SIMAM devolveu depois do Consultar: a tela de visualização e, se deu, o CSV exportado. */
    public record Retorno(String htmlVisualizacao, String csv, String urlExportacao) {
    }

    private static final Pattern EXPORT_BASE = Pattern.compile("\"ExportUrlBase\":\"([^\"]+)\"");
    private static final Pattern BOTAO = Pattern.compile("WebForm_PostBackOptions\\(&quot;([^&]+)&quot;|WebForm_PostBackOptions\\(\"([^\"]+)\"");

    private final URI urlFormulario;
    private final Duration espera;

    public SimamClient(@Value("${tce.simam.url:https://simam.tce.pr.gov.br/Paginas/Rel_LRF.aspx?relTipo=1}") String url,
                       @Value("${tce.simam.espera-ms:1500}") long esperaMs) {
        this.urlFormulario = URI.create(url);
        this.espera = Duration.ofMillis(esperaMs);
    }

    private static final class Sessao {
        final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        Document pagina;
        URI url;
    }

    /** Lê o estado dos filtros depois de aplicar as escolhas já feitas (para montar/atualizar o formulário). */
    public List<Filtro> filtros(Map<String, String> selecao) throws IOException, InterruptedException {
        Sessao s = abrir();
        aplicar(s, selecao);
        return lerFiltros(s.pagina);
    }

    public Retorno consultar(Map<String, String> selecao) throws IOException, InterruptedException {
        Sessao s = abrir();
        aplicar(s, selecao);
        for (Filtro f : lerFiltros(s.pagina)) {
            if (f.valor() == null || f.valor().isBlank()) {
                throw new IllegalArgumentException("Escolha o filtro \"" + f.rotulo() + "\" antes de consultar.");
            }
        }
        Element botao = s.pagina.selectFirst("a[id$=btnConsulta]");
        Matcher m = botao == null ? null : BOTAO.matcher(botao.outerHtml());
        if (m == null || !m.find()) {
            throw new IOException("Não encontrei o botão Consultar na página do SIMAM (o layout pode ter mudado).");
        }
        String alvo = m.group(1) != null ? m.group(1) : m.group(2);
        Map<String, String> campos = campos(s.pagina);
        campos.put("__EVENTTARGET", alvo);
        campos.put("__EVENTARGUMENT", "");
        postar(s, campos);

        String html = s.pagina.outerHtml();
        String base = exportBase(html);
        for (int i = 0; base == null && i < 3; i++) { // a tela de visualização às vezes carrega o relatório depois
            Thread.sleep(espera.toMillis());
            s.pagina = Jsoup.parse(get(s, s.url), s.url.toString());
            html = s.pagina.outerHtml();
            base = exportBase(html);
        }
        if (base == null) {
            return new Retorno(html, null, null);
        }
        URI exportacao = s.url.resolve(base + "CSV");
        Thread.sleep(espera.toMillis());
        return new Retorno(html, get(s, exportacao), exportacao.toString());
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

    /** Escolhe os filtros na ordem de dependência; cada select com AutoPostBack dispara o postback do site. */
    private void aplicar(Sessao s, Map<String, String> selecao) throws IOException, InterruptedException {
        for (String chave : ORDEM) {
            String valor = selecao == null ? null : selecao.get(chave);
            if (valor == null || valor.isBlank()) {
                break; // filtros seguintes dependem deste
            }
            Element sel = select(s.pagina, chave);
            if (sel == null || sel.hasAttr("disabled")) {
                break;
            }
            if (valor.equals(valorAtual(sel))) {
                continue;
            }
            if (sel.select("option").stream().noneMatch(o -> o.attr("value").equals(valor))) {
                throw new IllegalArgumentException("Opção inválida para o filtro \"" + chave + "\".");
            }
            Map<String, String> campos = campos(s.pagina);
            campos.put(sel.attr("name"), valor);
            if (sel.attr("onchange").contains("__doPostBack")) {
                campos.put("__EVENTTARGET", sel.attr("name"));
                campos.put("__EVENTARGUMENT", "");
                postar(s, campos);
            } else {
                // sem postback: o valor só vale no envio final; guardamos na própria página
                sel.select("option").forEach(o -> o.attr("selected", o.attr("value").equals(valor) ? "selected" : null));
            }
            Thread.sleep(200); // um pedido por vez, sem martelar o servidor
        }
    }

    private static Element select(Document pagina, String chave) {
        for (Element sel : pagina.select("select[id*=ddl]")) {
            if (chaveDe(sel).equals(chave)) {
                return sel;
            }
        }
        return null;
    }

    private static String chaveDe(Element sel) {
        String id = sel.id();
        String resto = id.substring(id.indexOf("ddl") + 3).toLowerCase(Locale.ROOT);
        return resto.replace("í", "i").replace("ó", "o").replace("é", "e");
    }

    private static String valorAtual(Element sel) {
        Element o = sel.selectFirst("option[selected]");
        return o != null ? o.attr("value") : sel.selectFirst("option") != null ? sel.selectFirst("option").attr("value") : "";
    }

    private static List<Filtro> lerFiltros(Document pagina) {
        List<Filtro> lista = new ArrayList<>();
        for (Element sel : pagina.select("select[id*=ddl]")) {
            String chave = chaveDe(sel);
            Element lbl = pagina.getElementById(sel.id().replace("ddl", "lbl"));
            String rotulo = lbl != null && !lbl.text().isBlank() ? lbl.text().trim() : Character.toUpperCase(chave.charAt(0)) + chave.substring(1);
            List<Opcao> opcoes = sel.select("option").stream()
                    .filter(o -> !o.attr("value").isBlank())
                    .map(o -> new Opcao(o.attr("value"), o.text().trim())).toList();
            lista.add(new Filtro(chave, sel.id(), rotulo, !sel.hasAttr("disabled"), valorAtual(sel), opcoes));
        }
        lista.sort(java.util.Comparator.comparingInt(f -> ORDEM.indexOf(f.chave()) < 0 ? 99 : ORDEM.indexOf(f.chave())));
        return lista;
    }

    private static Map<String, String> campos(Document pagina) {
        FormElement form = (FormElement) pagina.selectFirst("form[id=form2]") ;
        if (form == null) {
            form = pagina.select("form").stream().filter(f -> f instanceof FormElement).map(f -> (FormElement) f).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Formulário não encontrado na página do SIMAM."));
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (Connection.KeyVal kv : form.formData()) {
            m.put(kv.key(), kv.value());
        }
        return m;
    }

    private void postar(Sessao s, Map<String, String> campos) throws IOException, InterruptedException {
        StringBuilder corpo = new StringBuilder();
        campos.forEach((k, v) -> corpo.append(corpo.length() > 0 ? "&" : "")
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        HttpRequest req = HttpRequest.newBuilder(s.url).timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", s.url.toString())
                .header("User-Agent", "payroll-da/0.1 (consulta de relatorios LRF)")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())).build();
        HttpResponse<String> r = s.http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() >= 400) {
            throw new IOException("O SIMAM respondeu HTTP " + r.statusCode() + ".");
        }
        s.url = r.uri();
        s.pagina = Jsoup.parse(r.body(), r.uri().toString());
    }

    private String get(Sessao s, URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "payroll-da/0.1 (consulta de relatorios LRF)")
                .header("Referer", s.url == null ? uri.toString() : s.url.toString()).GET().build();
        HttpResponse<String> r = s.http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() >= 400) {
            throw new IOException("O SIMAM respondeu HTTP " + r.statusCode() + " em " + uri.getPath() + ".");
        }
        return r.body();
    }
}
