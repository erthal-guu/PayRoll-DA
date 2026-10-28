package br.com.payrollda.lrf.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige login em toda a /api, menos no próprio login. Importação, consulta ao SIMAM e
 * cadastro de usuários ficam só para o perfil ADMIN.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> SO_ADMIN = List.of("/api/importacoes", "/api/simam", "/api/usuarios");

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !uri.startsWith("/api/") || uri.equals("/api/auth/login") || "OPTIONS".equals(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Usuario u = auth.validar(token(req)).orElse(null);
        if (u == null) {
            responder(resp, HttpServletResponse.SC_UNAUTHORIZED, "Faça login para continuar.");
            return;
        }
        String uri = req.getRequestURI();
        if (!u.admin() && SO_ADMIN.stream().anyMatch(uri::startsWith)) {
            responder(resp, HttpServletResponse.SC_FORBIDDEN, "Só o administrador pode fazer isso.");
            return;
        }
        req.setAttribute(UsuarioAtual.ATRIBUTO, u);
        chain.doFilter(req, resp);
    }

    static String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).trim() : null;
    }

    private static void responder(HttpServletResponse resp, int status, String mensagem) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"mensagem\":\"" + mensagem + "\"}");
    }
}
