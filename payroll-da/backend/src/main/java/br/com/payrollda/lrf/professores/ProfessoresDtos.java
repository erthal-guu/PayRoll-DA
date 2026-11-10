package br.com.payrollda.lrf.professores;

import java.math.BigDecimal;
import java.util.List;

public final class ProfessoresDtos {

    private ProfessoresDtos() {
    }

    public record Piso(int ano, BigDecimal valor20h, String atoLegal) {
    }

    public record Nivel(String nome, BigDecimal percSobreBase) {
    }

    /** Estrutura do plano de carreira, como é cadastrada. */
    public record Plano(String codIbge, String municipio, int ano, int cargaHoraria, BigDecimal vencimentoBase,
                        BigDecimal percClasse, int classeFinal, String lei, List<Nivel> niveis) {
    }

    /** Uma linha (nível) das três tabelas, com um valor por classe. */
    public record LinhaNivel(String nome, BigDecimal percSobreBase, List<BigDecimal> atual, List<BigDecimal> conformidade,
                             List<BigDecimal> defasagem, List<Boolean> abaixoDoPiso) {
    }

    /**
     * Tabelas calculadas: vencimentos atuais, em conformidade com o piso e a defasagem mensal (atual menos conformidade).
     * piso é o valor proporcional à carga horária; indiceDefasagem é quanto a tabela atual precisa subir, em %.
     */
    public record Analise(Plano plano, Piso pisoAno, BigDecimal piso, List<String> classes, List<LinhaNivel> niveis,
                          BigDecimal indiceDefasagem, int celulasAbaixoDoPiso) {
    }

    public record PlanoResumo(String codIbge, String municipio, int ano, int cargaHoraria) {
    }
}
