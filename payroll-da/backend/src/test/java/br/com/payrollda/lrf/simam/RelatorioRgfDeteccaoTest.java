package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RelatorioRgfDeteccaoTest {

    @Test
    void reconheceTituloPartidoEmVariasCelulas() {
        assertTrue(RelatorioRgfParser.pareceRelatorioRgf("RGF - Demonstrativo da\nDespesa com   Pessoal - Poder Legislativo"));
    }

    @Test
    void errorMostraOQueOSimamDevolveu() {
        var e = assertThrows(RelatorioRgfParser.RelatorioInvalidoException.class,
                () -> RelatorioRgfParser.deCsv("Relatório não disponível para o período;\n", "4101507"));
        assertTrue(e.getMessage().contains("Relatório não disponível"), e.getMessage());
    }
}
