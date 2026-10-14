package br.com.payrollda.lrf.importacao;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Uma linha válida do demonstrativo de despesa com pessoal, já convertida. */
public record LinhaDespesa(
        String codIbge,
        String municipio,
        String entidade,
        Poder poder,
        int ano,
        int mes,
        BigDecimal despesaBruta,
        BigDecimal pessoalAtivo,
        BigDecimal inativosPensionistas,
        BigDecimal outrasDespesas,
        BigDecimal naoComputadas,
        BigDecimal despesaTotal,
        BigDecimal rclAjustada) {

    /** Despesa total com pessoal sobre a receita corrente líquida ajustada, em %. */
    public BigDecimal percentual() {
        if (rclAjustada.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return despesaTotal.multiply(BigDecimal.valueOf(100)).divide(rclAjustada, 4, RoundingMode.HALF_UP);
    }

    public enum Poder {
        EXECUTIVO(new BigDecimal("54.00")),
        LEGISLATIVO(new BigDecimal("6.00"));

        /** Limite máximo da LRF (art. 20, III) para a esfera municipal. */
        private final BigDecimal limiteMaximo;

        Poder(BigDecimal limiteMaximo) {
            this.limiteMaximo = limiteMaximo;
        }

        public BigDecimal limiteMaximo() {
            return limiteMaximo;
        }
    }
}
