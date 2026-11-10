package br.com.payrollda.lrf.fundeb;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.payrollda.lrf.auth.UsuarioAtual;

/** Série anual das transferências do FUNDEB do município (Prefeitura vê só o próprio). */
@RestController
@RequestMapping("/api/fundeb")
public class FundebController {

    private final FundebRepository repo;

    public FundebController(FundebRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{codIbge}")
    public List<FundebRepository.Ano> anos(@PathVariable String codIbge) {
        String restrito = UsuarioAtual.codIbgeRestrito();
        if (restrito != null && !restrito.equals(codIbge)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode ver o seu município.");
        }
        return repo.anos(codIbge);
    }
}
