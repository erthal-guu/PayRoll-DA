package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Simula o formulário ASP.NET do SIMAM (postbacks em cascata, Consultar e exportação em CSV). */
class SimamClientTest {

    private HttpServer servidor;
    private String base;

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/Paginas/Rel_LRF.aspx", ex -> {
            String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String html = formulario(corpo);
            if (corpo.contains("btnConsulta")) {
                ex.getResponseHeaders().add("Location", "/Paginas/VisualizarRelatorio.aspx");
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }
            responder(ex, html);
        });
        servidor.createContext("/Paginas/VisualizarRelatorio.aspx", ex -> responder(ex,
                "<html><body><script>var x={\"ExportUrlBase\":\"/Reserved.axd?Sessao=1\\u0026OpType=Export\\u0026Format=\"};</script></body></html>"));
        servidor.createContext("/Reserved.axd", ex -> {
            byte[] b = ("Tolerancia\nformato=" + ex.getRequestURI().getQuery()).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/Paginas/Rel_LRF.aspx?relTipo=1";
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange ex, String html) throws java.io.IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static String formulario(String corpo) {
        String d = URLDecoder.decode(corpo, StandardCharsets.UTF_8);
        boolean tipo = d.contains("ddlTipo=1");
        String cidade = tipo ? "<select name=\"ctl00$ContentPlaceHolder1$ddlMunicipio\" id=\"ContentPlaceHolder1_ddlMunicipio\" onchange=\"setTimeout('__doPostBack(\\'ctl00$ContentPlaceHolder1$ddlMunicipio\\',\\'\\')',0)\">"
                + "<option value=\"\">Selecione</option><option value=\"4101507\"" + (d.contains("ddlMunicipio=4101507") ? " selected=\"selected\"" : "") + ">ANDIRÁ</option></select>" : "";
        return "<html><body><form method=\"post\" action=\"/Paginas/Rel_LRF.aspx?relTipo=1\" id=\"form2\">"
                + "<input type=\"hidden\" name=\"__VIEWSTATE\" value=\"abc\"><input type=\"hidden\" name=\"__EVENTTARGET\" value=\"\">"
                + "<span id=\"ContentPlaceHolder1_lblTipo\">Tipo</span>"
                + "<select name=\"ctl00$ContentPlaceHolder1$ddlTipo\" id=\"ContentPlaceHolder1_ddlTipo\" onchange=\"setTimeout('__doPostBack(\\'ctl00$ContentPlaceHolder1$ddlTipo\\',\\'\\')',0)\">"
                + "<option value=\"\"" + (tipo ? "" : " selected=\"selected\"") + ">Selecione o Tipo</option><option value=\"1\"" + (tipo ? " selected=\"selected\"" : "") + ">Município</option><option value=\"2\">Estado</option></select>"
                + cidade
                + "<a id=\"ContentPlaceHolder1_btnConsulta\" href=\"javascript:WebForm_DoPostBackWithOptions(new WebForm_PostBackOptions(&quot;ctl00$ContentPlaceHolder1$btnConsulta&quot;, &quot;&quot;, true))\">Consultar</a>"
                + "</form></body></html>";
    }

    @Test
    void liberaOsFiltrosSeguintesAposCadaEscolha() throws Exception {
        SimamClient c = new SimamClient(base, 0, System.getProperty("java.io.tmpdir"));
        assertEquals(1, c.filtros(Map.of()).size());
        var f = c.filtros(Map.of("tipo", "1"));
        assertEquals(2, f.size());
        assertEquals("municipio", f.get(1).chave());
        assertEquals("ANDIRÁ", f.get(1).opcoes().get(0).rotulo());
    }

    @Test
    void consultaEBaixaOCsvPelaExportacao() throws Exception {
        SimamClient c = new SimamClient(base, 0, System.getProperty("java.io.tmpdir"));
        var r = c.consultar(Map.of("tipo", "1", "municipio", "4101507"));
        assertTrue(r.csv().contains("Format=CSV"), r.csv());
    }

    @Test
    void avisaQuandoOSimamNaoAceitaAEscolha() {
        SimamClient c = new SimamClient(base, 0, System.getProperty("java.io.tmpdir"));
        var e = assertThrows(java.io.IOException.class, () -> c.filtros(Map.of("tipo", "2")));
        assertTrue(e.getMessage().contains("não aceitou a escolha do campo \"tipo\""), e.getMessage());
    }

    @Test
    void ignoraAOpcaoSelecione() throws Exception {
        SimamClient c = new SimamClient(base, 0, System.getProperty("java.io.tmpdir"));
        var tipo = c.filtros(Map.of()).get(0);
        assertEquals("", tipo.valor());
        assertEquals(2, tipo.opcoes().size());
    }
}
