package br.com.payrollda.lrf.auth;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Usuário logado na requisição atual (colocado pelo AuthFilter). */
public final class UsuarioAtual {

    static final String ATRIBUTO = "payroll.usuario";

    private UsuarioAtual() {
    }

    public static Usuario get() {
        RequestAttributes req = RequestContextHolder.getRequestAttributes();
        return req == null ? null : (Usuario) req.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
    }

    /** Município ao qual o usuário está preso, ou null quando ele vê todos (admin ou fora de uma requisição). */
    public static String codIbgeRestrito() {
        Usuario u = get();
        return u == null || u.admin() ? null : u.codIbge();
    }
}
