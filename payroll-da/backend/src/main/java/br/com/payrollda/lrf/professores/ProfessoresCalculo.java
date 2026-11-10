package br.com.payrollda.lrf.professores;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import br.com.payrollda.lrf.professores.ProfessoresDtos.Analise;
import br.com.payrollda.lrf.professores.ProfessoresDtos.LinhaNivel;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Nivel;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Piso;
import br.com.payrollda.lrf.professores.ProfessoresDtos.Plano;

/**
 * Monta as tabelas de vencimentos do magistério. Regra do plano de carreira: cada nível soma o seu percentual sobre o
 * vencimento base; a primeira coluna é a classe "1-2" e cada classe seguinte soma o percentual de classe sobre a
 * anterior. A tabela em conformidade usa a mesma estrutura partindo do piso (proporcional à carga horária).
 */
public final class ProfessoresCalculo {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private ProfessoresCalculo() {
    }

    public static List<String> classes(int classeFinal) {
        List<String> c = new ArrayList<>();
        c.add("1-2");
        for (int i = 3; i <= classeFinal; i++) {
            c.add(String.valueOf(i));
        }
        return c;
    }

    /** Piso para a carga horária do plano (o PSPN é fixado para 40 h; aqui guardamos o de 20 h). */
    public static BigDecimal pisoProporcional(BigDecimal valor20h, int cargaHoraria) {
        return valor20h.multiply(BigDecimal.valueOf(cargaHoraria)).divide(BigDecimal.valueOf(20), 2, RoundingMode.HALF_UP);
    }

    /**
     * Valores de um nível partindo de um vencimento inicial, sem arredondar. O percentual de classe é composto sem
     * arredondar a cada passo e a defasagem é a diferença dos valores sem arredondar; só o resultado de cada célula é
     * arredondado (é o que reproduz as tabelas do relatório de Jussara-PR).
     */
    static List<BigDecimal> linha(BigDecimal inicial, BigDecimal percNivel, BigDecimal percClasse, int colunas) {
        List<BigDecimal> v = new ArrayList<>(colunas);
        BigDecimal atual = inicial.multiply(BigDecimal.ONE.add(percNivel.divide(CEM, MathContext.DECIMAL128)));
        BigDecimal fator = BigDecimal.ONE.add(percClasse.divide(CEM, MathContext.DECIMAL128));
        for (int i = 0; i < colunas; i++) {
            v.add(atual);
            atual = atual.multiply(fator, MathContext.DECIMAL128);
        }
        return v;
    }

    public static Analise analisar(Plano plano, Piso pisoAno) {
        List<String> classes = classes(plano.classeFinal());
        int n = classes.size();
        BigDecimal piso = pisoAno == null ? null : pisoProporcional(pisoAno.valor20h(), plano.cargaHoraria());
        // se o vencimento base já está acima do piso, a tabela já está em conformidade
        BigDecimal baseConforme = piso == null ? plano.vencimentoBase() : piso.max(plano.vencimentoBase());
        List<LinhaNivel> niveis = new ArrayList<>();
        int abaixo = 0;
        for (Nivel nv : plano.niveis()) {
            List<BigDecimal> atualExato = linha(plano.vencimentoBase(), nv.percSobreBase(), plano.percClasse(), n);
            List<BigDecimal> confExato = linha(baseConforme, nv.percSobreBase(), plano.percClasse(), n);
            List<BigDecimal> atual = new ArrayList<>(n);
            List<BigDecimal> conf = new ArrayList<>(n);
            List<BigDecimal> def = new ArrayList<>(n);
            List<Boolean> abaixoPiso = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                atual.add(atualExato.get(i).setScale(2, RoundingMode.HALF_UP));
                conf.add(confExato.get(i).setScale(2, RoundingMode.HALF_UP));
                def.add(atualExato.get(i).subtract(confExato.get(i)).setScale(2, RoundingMode.HALF_UP));
                boolean b = piso != null && atual.get(i).compareTo(piso) < 0;
                abaixoPiso.add(b);
                if (b) {
                    abaixo++;
                }
            }
            niveis.add(new LinhaNivel(nv.nome(), nv.percSobreBase(), atual, conf, def, abaixoPiso));
        }
        BigDecimal indice = BigDecimal.ZERO.setScale(2);
        if (piso != null && piso.compareTo(plano.vencimentoBase()) > 0) {
            indice = piso.divide(plano.vencimentoBase(), 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                    .multiply(CEM).setScale(2, RoundingMode.HALF_UP);
        }
        return new Analise(plano, pisoAno, piso, classes, niveis, indice, abaixo);
    }
}
