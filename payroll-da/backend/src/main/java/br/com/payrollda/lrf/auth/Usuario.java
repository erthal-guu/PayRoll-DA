package br.com.payrollda.lrf.auth;

/** Usuário do painel. codIbge é null para ADMIN e obrigatório para PREFEITURA. */
public record Usuario(long id, String nome, String email, String perfil, String codIbge, String municipio, boolean ativo) {

    public boolean admin() {
        return "ADMIN".equals(perfil);
    }
}
