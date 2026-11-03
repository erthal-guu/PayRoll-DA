package br.com.payrollda.lrf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceEdicaoTest {

    private final Usuario admin = new Usuario(1, "Admin", "admin@x.br", "ADMIN", null, null, true);
    private final Usuario outroAdmin = new Usuario(2, "Outro", "outro@x.br", "ADMIN", null, null, true);
    private final Usuario prefeitura = new Usuario(3, "Pref", "pref@x.br", "PREFEITURA", "4106902", "Curitiba", true);

    private UsuarioRepository repo;
    private AuthService auth;

    @BeforeEach
    void preparar() {
        repo = mock(UsuarioRepository.class);
        auth = new AuthService(repo);
        when(repo.buscar(1)).thenReturn(Optional.of(admin));
        when(repo.buscar(2)).thenReturn(Optional.of(outroAdmin));
        when(repo.buscar(3)).thenReturn(Optional.of(prefeitura));
        when(repo.contarAdminsAtivos()).thenReturn(2L);
    }

    @Test
    void editaDadosSemTrocarSenha() {
        Usuario u = auth.editarUsuario(admin, 3, " Pref Nova ", "nova@x.br", "", "prefeitura", "4113700", "Londrina", true);

        assertThat(u).isEqualTo(new Usuario(3, "Pref Nova", "nova@x.br", "PREFEITURA", "4113700", "Londrina", true));
        verify(repo).atualizar(3, "Pref Nova", "nova@x.br", "PREFEITURA", "4113700", "Londrina", true);
        verify(repo, never()).trocarSenha(anyLong(), anyString());
        verify(repo, never()).encerrarSessoes(anyLong());
    }

    @Test
    void senhaInformadaVaiComBcrypt() {
        auth.editarUsuario(admin, 3, "Pref", "pref@x.br", "nova123", "PREFEITURA", "4106902", "Curitiba", true);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).trocarSenha(eq(3L), hash.capture());
        assertThat(new BCryptPasswordEncoder().matches("nova123", hash.getValue())).isTrue();
    }

    @Test
    void senhaCurtaEhRecusada() {
        assertThatThrownBy(() -> auth.editarUsuario(admin, 3, "Pref", "pref@x.br", "123", "PREFEITURA", "4106902", "Curitiba", true))
                .hasMessageContaining("6 caracteres");
        verify(repo, never()).atualizar(anyLong(), any(), any(), any(), any(), any(), eq(true));
    }

    @Test
    void emailDeOutroUsuarioEhRecusado() {
        when(repo.emailEmUsoPorOutro("outro@x.br", 3)).thenReturn(true);

        assertThatThrownBy(() -> auth.editarUsuario(admin, 3, "Pref", "outro@x.br", null, "PREFEITURA", "4106902", "Curitiba", true))
                .hasMessageContaining("e-mail");
    }

    @Test
    void prefeituraExigeMunicipio() {
        assertThatThrownBy(() -> auth.editarUsuario(admin, 2, "Outro", "outro@x.br", null, "PREFEITURA", "", null, true))
                .hasMessageContaining("município");
    }

    @Test
    void viraAdminLimpaMunicipio() {
        Usuario u = auth.editarUsuario(admin, 3, "Pref", "pref@x.br", null, "ADMIN", "4106902", "Curitiba", true);

        assertThat(u.codIbge()).isNull();
        assertThat(u.municipio()).isNull();
        verify(repo).atualizar(3, "Pref", "pref@x.br", "ADMIN", null, null, true);
    }

    @Test
    void adminNaoSeRebaixa() {
        assertThatThrownBy(() -> auth.editarUsuario(admin, 1, "Admin", "admin@x.br", null, "PREFEITURA", "4106902", "Curitiba", true))
                .hasMessageContaining("próprio perfil");
    }

    @Test
    void adminNaoSeDesativa() {
        assertThatThrownBy(() -> auth.editarUsuario(admin, 1, "Admin", "admin@x.br", null, "ADMIN", null, null, false))
                .hasMessageContaining("próprio perfil");
    }

    @Test
    void adminEditaOProprioNomeEEmail() {
        auth.editarUsuario(admin, 1, "Admin Novo", "admin2@x.br", null, "ADMIN", null, null, null);

        verify(repo).atualizar(1, "Admin Novo", "admin2@x.br", "ADMIN", null, null, true);
    }

    @Test
    void naoRemoveOUltimoAdministradorAtivo() {
        when(repo.contarAdminsAtivos()).thenReturn(1L);

        assertThatThrownBy(() -> auth.editarUsuario(admin, 2, "Outro", "outro@x.br", null, "ADMIN", null, null, false))
                .hasMessageContaining("pelo menos um administrador");
    }

    @Test
    void desativarEncerraSessoes() {
        Usuario u = auth.editarUsuario(admin, 2, "Outro", "outro@x.br", null, "ADMIN", null, null, false);

        assertThat(u.ativo()).isFalse();
        verify(repo).encerrarSessoes(2);
    }

    @Test
    void usuarioInexistenteDa404() {
        when(repo.buscar(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.editarUsuario(admin, 99, "X", "x@x.br", null, "ADMIN", null, null, true))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
