package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Lê os filtros de páginas reais do SIMAM salvas pelo navegador (tipo Município e Consórcio). */
class SimamPaginaRealTest {

    private static List<SimamClient.Filtro> ler(String arquivo) throws Exception {
        String html = new String(SimamPaginaRealTest.class.getResourceAsStream("/simam/" + arquivo).readAllBytes(), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(html, "https://simam.tce.pr.gov.br/Paginas/Rel_LRF.aspx?relTipo=1");
        Method m = SimamClient.class.getDeclaredMethod("lerFiltros", Document.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<SimamClient.Filtro> f = (List<SimamClient.Filtro>) m.invoke(null, doc);
        return f;
    }

    @Test
    void municipioTemMunicipioEntidadeRelatorioEAno() throws Exception {
        var f = ler("rel-lrf-municipio.html");
        assertEquals(List.of("tipo", "municipio", "entidade", "relatorio", "ano"), f.stream().map(SimamClient.Filtro::chave).toList());
        assertEquals("1", f.get(0).valor());
        assertEquals("Município", f.get(1).rotulo());
        assertEquals(true, f.get(1).opcoes().size() > 300);
    }

    @Test
    void consorcioVaiDoTipoParaEntidade() throws Exception {
        var f = ler("rel-lrf-consorcio.html");
        assertEquals(List.of("tipo", "entidade", "relatorio", "ano"), f.stream().map(SimamClient.Filtro::chave).toList());
        assertEquals("2", f.get(0).valor());
    }
}
