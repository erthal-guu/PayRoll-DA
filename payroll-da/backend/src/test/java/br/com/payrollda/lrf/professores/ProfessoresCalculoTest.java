package br.com.payrollda.lrf.professores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.professores.ProfessoresDtos.Analise;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Nivel;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Piso;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Plano;

/** Confere o cálculo com as tabelas do relatório de Jussara-PR (PSPN 2026, professor 20 horas). */
class ProfessoresCalculoTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static final Plano JUSSARA = new Plano("4112702", "Jussara", 2026, 20, d("2236.79"), d("2"), 30, "Lei nº 2036/2026",
            List.of(new Nivel("I - Magistério", d("0")), new Nivel("II - Licenciatura", d("16")), new Nivel("III - Pós-graduação", d("19.5"))));

    private static final Piso PSPN_2026 = new Piso(2026, d("2565.32"), "Portaria MEC nº 82/2026");

    @Test
    void reproduzAsTabelasDoRelatorio() {
        Analise a = ProfessoresCalculo.analisar(JUSSARA, PSPN_2026);
        assertEquals(29, a.classes().size());
        assertEquals("1-2", a.classes().get(0));
        assertEquals("30", a.classes().get(28));

        var i = a.niveis().get(0);
        var ii = a.niveis().get(1);
        var iii = a.niveis().get(2);
        // tabela atual
        assertEquals(d("2236.79"), i.atual().get(0));
        assertEquals(d("2281.53"), i.atual().get(1));
        assertEquals(d("2951.40"), i.atual().get(14));
        assertEquals(d("3894.31"), i.atual().get(28));
        assertEquals(d("2594.68"), ii.atual().get(0));
        assertEquals(d("4517.39"), ii.atual().get(28));
        assertEquals(d("2672.96"), iii.atual().get(0));
        assertEquals(d("4653.70"), iii.atual().get(28));
        // tabela em conformidade
        assertEquals(d("2565.32"), i.conformidade().get(0));
        assertEquals(d("4466.28"), i.conformidade().get(28));
        assertEquals(d("2975.77"), ii.conformidade().get(0));
        assertEquals(d("5180.89"), ii.conformidade().get(28));
        assertEquals(d("3065.56"), iii.conformidade().get(0));
        assertEquals(d("5337.21"), iii.conformidade().get(28));
        // defasagem
        assertEquals(d("-328.53"), i.defasagem().get(0));
        assertEquals(d("-571.98"), i.defasagem().get(28));
        assertEquals(d("-683.51"), iii.defasagem().get(28));
        // índice de defasagem em relação ao PSPN
        assertEquals(d("14.69"), a.indiceDefasagem());
    }

    @Test
    void marcaOsVencimentosAbaixoDoPiso() {
        Analise a = ProfessoresCalculo.analisar(JUSSARA, PSPN_2026);
        var i = a.niveis().get(0);
        assertTrue(i.abaixoDoPiso().get(0));   // classe 1-2: 2.236,79
        assertTrue(i.abaixoDoPiso().get(6));   // classe 8: 2.518,99
        assertFalse(i.abaixoDoPiso().get(7));  // classe 9: 2.569,37
        assertFalse(a.niveis().get(1).abaixoDoPiso().get(0)); // II, classe 1-2: 2.594,68
        assertEquals(7, a.celulasAbaixoDoPiso());
    }

    @Test
    void semDefasagemQuandoOBaseJaPassaDoPiso() {
        Plano acima = new Plano("1", "X", 2026, 20, d("3000.00"), d("2"), 10, null, List.of(new Nivel("I", d("0"))));
        Analise a = ProfessoresCalculo.analisar(acima, PSPN_2026);
        assertEquals(d("0.00"), a.indiceDefasagem());
        assertEquals(0, a.celulasAbaixoDoPiso());
        assertEquals(d("0.00"), a.niveis().get(0).defasagem().get(0));
    }

    @Test
    void pisoProporcionalACargaHoraria() {
        assertEquals(d("5130.64"), ProfessoresCalculo.pisoProporcional(d("2565.32"), 40));
    }
}
