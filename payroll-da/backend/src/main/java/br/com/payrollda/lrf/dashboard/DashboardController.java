package br.com.payrollda.lrf.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.payrollda.lrf.dashboard.DashboardDtos.EntidadeLinha;
import br.com.payrollda.lrf.dashboard.DashboardDtos.FaixasNoPeriodo;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Municipio;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Pagina;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Periodo;
import br.com.payrollda.lrf.dashboard.DashboardDtos.PontoEvolucao;
import br.com.payrollda.lrf.dashboard.DashboardDtos.Resumo;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardRepository repo;

    public DashboardController(DashboardRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/periodos")
    public List<Periodo> periodos() {
        return repo.periodos();
    }

    @GetMapping("/municipios")
    public List<Municipio> municipios() {
        return repo.municipios();
    }

    @GetMapping("/resumo")
    public ResponseEntity<Resumo> resumo(@RequestParam(required = false) Integer ano,
                                         @RequestParam(required = false) Integer mes,
                                         @RequestParam(required = false) String poder,
                                         @RequestParam(required = false) String codIbge,
                                         @RequestParam(required = false) String busca) {
        Filtro f = comPeriodoPadrao(new Filtro(ano, mes, poder, codIbge, null, busca));
        return f == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(repo.resumo(f));
    }

    @GetMapping("/entidades")
    public Pagina<EntidadeLinha> entidades(@RequestParam(required = false) Integer ano,
                                           @RequestParam(required = false) Integer mes,
                                           @RequestParam(required = false) String poder,
                                           @RequestParam(required = false) String codIbge,
                                           @RequestParam(required = false) String faixa,
                                           @RequestParam(required = false) String busca,
                                           @RequestParam(defaultValue = "usoLimite") String ordem,
                                           @RequestParam(defaultValue = "desc") String direcao,
                                           @RequestParam(defaultValue = "0") int pagina,
                                           @RequestParam(defaultValue = "20") int tamanho) {
        Filtro f = comPeriodoPadrao(new Filtro(ano, mes, poder, codIbge, faixa, busca));
        int tam = Math.min(Math.max(tamanho, 1), 200);
        if (f == null) {
            return new Pagina<>(List.of(), 0, 0, tam);
        }
        return repo.entidades(f, ordem, direcao, Math.max(pagina, 0), tam);
    }

    @GetMapping("/municipios/{codIbge}/entidades")
    public List<DashboardDtos.EntidadeResumo> entidadesDoMunicipio(@PathVariable String codIbge) {
        return repo.entidadesDoMunicipio(codIbge);
    }

    @GetMapping("/entidades/{id}/demonstrativo")
    public ResponseEntity<DashboardDtos.Demonstrativo> demonstrativo(@PathVariable long id, @RequestParam int ano,
                                                                     @RequestParam int mes) {
        return ResponseEntity.of(repo.demonstrativo(id, ano, mes));
    }

    @GetMapping("/entidades/{id}/evolucao")
    public List<PontoEvolucao> evolucaoEntidade(@PathVariable long id) {
        return repo.evolucaoEntidade(id);
    }

    @GetMapping("/evolucao-faixas")
    public List<FaixasNoPeriodo> evolucaoFaixas(@RequestParam(required = false) String poder,
                                                @RequestParam(required = false) String codIbge,
                                                @RequestParam(required = false) String busca) {
        return repo.evolucaoFaixas(new Filtro(null, null, poder, codIbge, null, busca));
    }

    /** Sem ano/mês informados, usa o período mais recente importado. Null se o banco estiver vazio. */
    private Filtro comPeriodoPadrao(Filtro f) {
        if (f.ano() != null && f.mes() != null) {
            return f;
        }
        return repo.ultimoPeriodo()
                .map(p -> f.comPeriodo(f.ano() != null ? f.ano() : p.ano(), f.mes() != null ? f.mes() : p.mes()))
                .orElse(null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> requisicaoInvalida(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }
}
