package br.com.payrollda.lrf.simam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.payrollda.lrf.dashboard.DashboardRepository;
import br.com.payrollda.lrf.fundeb.FundebRepository;
import br.com.payrollda.lrf.importacao.ImportacaoDto;
import br.com.payrollda.lrf.importacao.ImportacaoRepository;
import br.com.payrollda.lrf.importacao.ImportacaoService;

/** Relatório MDE: importa só o bimestre mais recente publicado e grava as transferências do FUNDEB. */
class SimamServiceMdeTest {

    @Test
    void importaOUltimoBimestreQueTrouxeOFundeb() throws Exception {
        String csv = Files.readString(Path.of(getClass().getResource("/simam/exportacao-mde-adrianopolis-2016.csv").toURI()), StandardCharsets.UTF_8);
        SimamClient client = mock(SimamClient.class);
        ImportacaoRepository importacoes = mock(ImportacaoRepository.class);
        FundebRepository fundeb = mock(FundebRepository.class);
        Map<String, String> selecao = Map.of("tipo", "1", "municipio", "4100301", "entidade", "9", "relatorio", "mde", "ano", "2016");
        when(client.filtros(selecao)).thenReturn(List.of(
                new SimamClient.Filtro("relatorio", "r", "Relatório", true, "mde", List.of(
                        new SimamClient.Opcao("rgf", "RGF - Demonstrativo da Despesa com Pessoal"),
                        new SimamClient.Opcao("mde", "RREO - Demonstrativo das Receitas e Despesas com Manutenção e Desenvolvimento do Ensino - MDE"))),
                new SimamClient.Filtro("periodo", "p", "Período", true, null, List.of(
                        new SimamClient.Opcao("1", "1º Bimestre"), new SimamClient.Opcao("2", "2º Bimestre"), new SimamClient.Opcao("3", "3º Bimestre")))));
        // o 3º bimestre ainda não tem o demonstrativo; o 2º tem
        when(client.consultar(argThat(m -> m != null && "3".equals(m.get("periodo")))))
                .thenReturn(new SimamClient.Retorno("<html>sem dados</html>", "dsItem\nnada,\" 0,00\"\n", null, Map.of("periodo", "3º Bimestre")));
        when(client.consultar(argThat(m -> m != null && "2".equals(m.get("periodo")))))
                .thenReturn(new SimamClient.Retorno("<html></html>", csv, null,
                        Map.of("municipio", "ADRIANÓPOLIS", "ano", "2016", "periodo", "2º Bimestre")));
        when(importacoes.criar(anyString(), anyString())).thenReturn(7L);
        when(importacoes.buscar(7L)).thenReturn(Optional.of(mock(ImportacaoDto.class)));
        when(fundeb.anos("4100301")).thenReturn(List.of(new FundebRepository.Ano("4100301", "Adrianópolis", 2016, "2º Bimestre", 4,
                new BigDecimal("853455.00"), null, null, null, null, null, null, null)));

        SimamService service = new SimamService(client, mock(ImportacaoService.class), importacoes, mock(DashboardRepository.class),
                fundeb, Files.createTempDirectory("diag").toString());
        var r = service.consultar(selecao);

        verify(fundeb).salvar(eq("4100301"), eq("Adrianópolis"), eq(2016), eq("2º Bimestre"), eq(4),
                argThat(f -> new BigDecimal("853455.00").compareTo(f.transferido()) == 0));
        verify(client, never()).consultar(argThat(m -> m != null && "1".equals(m.get("periodo"))));
        verify(fundeb, times(1)).salvar(anyString(), anyString(), anyInt(), anyString(), anyInt(), any());
        assertEquals(2016, r.fundeb().ano());
    }
}
