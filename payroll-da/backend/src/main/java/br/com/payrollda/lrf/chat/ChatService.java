package br.com.payrollda.lrf.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.payrollda.lrf.dashboard.DashboardRepository;
import br.com.payrollda.lrf.dashboard.Filtro;

/**
 * Assistente do painel usando o Groq (API compatível com OpenAI) com ferramentas:
 * o modelo consulta o banco pelas mesmas consultas do dashboard (nunca inventa números)
 * e devolve sugestões de ajuste que o usuário aplica com um clique no front.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_RODADAS = 6;

    static final String INSTRUCOES = """
            Você é o assistente do Payroll DA, que mostra a despesa com pessoal das prefeituras e câmaras
            municipais do Paraná a partir do Demonstrativo da Despesa com Pessoal (RGF) do TCE-PR.

            Regras da LRF que você deve usar:
            - Limite máximo: Executivo municipal 54% e Legislativo municipal 6% da receita corrente líquida (RCL) ajustada.
            - Limite de alerta: 90% do máximo (48,6% e 5,4%). O Tribunal de Contas alerta o ente (art. 59, §1º, II).
            - Limite prudencial: 95% do máximo (51,3% e 5,7%). Acima dele ficam vedados, entre outros, concessão de
              vantagem ou reajuste (salvo revisão geral), criação de cargos, alteração de estrutura de carreira que
              aumente despesa, novas admissões (salvo reposição em educação, saúde e segurança) e hora extra (art. 22).
            - Acima do máximo: o excedente deve ser eliminado nos dois quadrimestres seguintes, pelo menos um terço no
              primeiro, com medidas como reduzir em pelo menos 20% as despesas com cargos em comissão e funções de
              confiança e exonerar servidores não estáveis (art. 23 e art. 169 da Constituição).

            Como trabalhar:
            - Para qualquer número, use as ferramentas. Nunca invente valores, nomes ou percentuais.
            - Responda em português, direto e curto: no máximo uns 8 parágrafos curtos ou listas.
            - Ao analisar uma entidade, diga a situação, a tendência dos últimos meses e a margem em reais até o
              limite, e sugira medidas proporcionais à faixa. Deixe claro que são sugestões, não parecer jurídico.
            - Quando um filtro, ordenação ou entidade no painel ajudar o usuário, chame sugerir_ajuste (até 3 vezes).
              O usuário vê um botão "Aplicar"; não diga que você já mudou a tela.
            - Valores em reais no formato brasileiro (R$ 1,2 mi), percentuais com vírgula (51,3%).
            """;

    private final DashboardRepository repo;
    private final ObjectMapper json;
    private final RestClient http;
    private final String apiKey;
    private final String url;
    private volatile String modelo;
    private final int maxTokens;

    public ChatService(DashboardRepository repo, ObjectMapper json,
                       @Value("${groq.api-key:}") String apiKey,
                       @Value("${groq.modelo:llama-3.3-70b-versatile}") String modelo,
                       @Value("${groq.max-tokens:1500}") int maxTokens,
                       @Value("${groq.url:https://api.groq.com/openai/v1/chat/completions}") String url) {
        this.repo = repo;
        this.json = json;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.maxTokens = maxTokens;
        this.url = url;
        this.http = RestClient.create();
    }

    public record Mensagem(String papel, String texto) {
    }

    public record Contexto(String tela, Map<String, Object> filtros, Long entidadeId) {
    }

    public record Sugestao(String rotulo, String motivo, Map<String, Object> filtros, Long entidadeId, String tela) {
    }

    public record Resposta(String texto, List<Sugestao> sugestoes) {
    }

    public Resposta conversar(List<Mensagem> historico, Contexto contexto) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("O assistente não está configurado. Defina GROQ_API_KEY no servidor.");
        }
        List<Map<String, Object>> mensagens = montarMensagens(historico, contexto);
        List<Sugestao> sugestoes = new ArrayList<>();
        StringBuilder texto = new StringBuilder();

        for (int rodada = 0; rodada < MAX_RODADAS; rodada++) {
            JsonNode msg = chamarApi(mensagens).path("choices").path(0).path("message");
            String conteudo = msg.path("content").asText("");
            if (!conteudo.isBlank()) {
                if (!texto.isEmpty()) {
                    texto.append("\n\n");
                }
                texto.append(conteudo.trim());
            }
            JsonNode chamadas = msg.path("tool_calls");
            if (!chamadas.isArray() || chamadas.isEmpty()) {
                break;
            }
            mensagens.add(json.convertValue(msg, Map.class));
            for (JsonNode chamada : chamadas) {
                String saida;
                try {
                    JsonNode args = json.readTree(chamada.path("function").path("arguments").asText("{}"));
                    saida = executarFerramenta(chamada.path("function").path("name").asText(), args, sugestoes);
                } catch (Exception e) {
                    saida = "Erro: " + e.getMessage();
                }
                mensagens.add(Map.of("role", "tool", "tool_call_id", chamada.path("id").asText(), "content", saida));
            }
        }
        String final_ = texto.isEmpty() ? "Pronto. Veja as sugestões abaixo." : texto.toString();
        return new Resposta(final_, sugestoes);
    }

    private List<Map<String, Object>> montarMensagens(List<Mensagem> historico, Contexto contexto) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", INSTRUCOES));
        // mantém só as últimas 12 mensagens para controlar custo
        List<Mensagem> recentes = historico.subList(Math.max(0, historico.size() - 12), historico.size());
        for (int i = 0; i < recentes.size(); i++) {
            Mensagem m = recentes.get(i);
            String papel = "assistant".equals(m.papel()) ? "assistant" : "user";
            String conteudo = m.texto();
            if (i == recentes.size() - 1 && "user".equals(papel) && contexto != null) {
                conteudo = "[Tela atual do painel: " + contexto.tela() + "; filtros: " + contexto.filtros()
                        + (contexto.entidadeId() != null ? "; entidade aberta no detalhe: id " + contexto.entidadeId() : "")
                        + "]\n\n" + conteudo;
            }
            msgs.add(Map.of("role", papel, "content", conteudo));
        }
        if (msgs.size() < 2 || !"user".equals(msgs.get(1).get("role"))) {
            throw new IllegalArgumentException("A conversa deve começar com uma mensagem do usuário.");
        }
        return msgs;
    }

    private JsonNode chamarApi(List<Map<String, Object>> mensagens) {
        try {
            return enviar(mensagens);
        } catch (RestClientResponseException e) {
            // o Groq aposenta modelos de tempos em tempos; se o configurado sumiu, troca por um disponível e tenta de novo
            if (e.getStatusCode().value() == 404 && trocarModelo()) {
                try {
                    return enviar(mensagens);
                } catch (RestClientResponseException e2) {
                    throw falha(e2);
                }
            }
            throw falha(e);
        }
    }

    private JsonNode enviar(List<Map<String, Object>> mensagens) {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "max_tokens", maxTokens,
                "temperature", 0.3,
                "tools", Ferramentas.DEFINICOES,
                "messages", mensagens);
        try {
            String resp = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(corpo)
                    .retrieve()
                    .body(String.class);
            return json.readTree(resp);
        } catch (RestClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível falar com o assistente: " + e.getMessage(), e);
        }
    }

    /** Escolhe outro modelo de chat na lista do Groq. Devolve false se não achou um diferente do atual. */
    private boolean trocarModelo() {
        try {
            String base = url.replaceFirst("/chat/completions/?$", "");
            JsonNode lista = json.readTree(http.get().uri(base + "/models")
                    .header("Authorization", "Bearer " + apiKey).retrieve().body(String.class)).path("data");
            List<String> ids = new ArrayList<>();
            lista.forEach(m -> ids.add(m.path("id").asText()));
            String novo = null;
            for (String preferido : List.of("llama-3.3", "gpt-oss-120b", "llama", "gpt-oss", "qwen", "kimi")) {
                novo = ids.stream().filter(id -> id.contains(preferido) && !id.contains("guard") && !id.contains("whisper")
                        && !id.contains("tts")).findFirst().orElse(null);
                if (novo != null) {
                    break;
                }
            }
            if (novo == null || novo.equals(modelo)) {
                return false;
            }
            log.warn("Modelo {} não encontrado no Groq; usando {}. Defina GROQ_MODELO para fixar outro.", modelo, novo);
            modelo = novo;
            return true;
        } catch (Exception e) {
            log.warn("Não consegui listar os modelos do Groq: {}", e.getMessage());
            return false;
        }
    }

    private IllegalStateException falha(RestClientResponseException e) {
        log.warn("API do Groq respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        String detalhe = "";
        try {
            detalhe = json.readTree(e.getResponseBodyAsString()).path("error").path("message").asText("");
        } catch (Exception ignorado) {
            // corpo sem JSON
        }
        return new IllegalStateException("O assistente está indisponível agora (HTTP " + e.getStatusCode().value() + ")"
                + (detalhe.isBlank() ? "." : ": " + detalhe));
    }

    private String executarFerramenta(String nome, JsonNode in, List<Sugestao> sugestoes) throws Exception {
        return switch (nome) {
            case "resumo_painel" -> json.writeValueAsString(repo.resumo(periodo(filtro(in, false))));
            case "buscar_entidades" -> {
                int limite = Math.min(Math.max(in.path("limite").asInt(10), 1), 20);
                yield json.writeValueAsString(repo.entidades(periodo(filtro(in, true)),
                        texto(in, "ordem", "usoLimite"), texto(in, "direcao", "desc"), 0, limite));
            }
            case "evolucao_entidade" -> {
                var serie = repo.evolucaoEntidade(in.path("entidadeId").asLong());
                yield json.writeValueAsString(serie.subList(Math.max(0, serie.size() - 12), serie.size()));
            }
            case "sugerir_ajuste" -> {
                if (sugestoes.size() >= 3) {
                    yield "Limite de 3 sugestões atingido.";
                }
                sugestoes.add(new Sugestao(texto(in, "rotulo", "Aplicar"), texto(in, "motivo", ""),
                        in.has("filtros") ? json.convertValue(in.get("filtros"), Map.class) : Map.of(),
                        in.hasNonNull("entidadeId") ? in.get("entidadeId").asLong() : null,
                        texto(in, "tela", "dashboard")));
                yield "Sugestão registrada; o usuário verá um botão para aplicar.";
            }
            default -> throw new IllegalArgumentException("Ferramenta desconhecida: " + nome);
        };
    }

    private Filtro filtro(JsonNode in, boolean comFaixa) {
        return new Filtro(in.hasNonNull("ano") ? in.get("ano").asInt() : null,
                in.hasNonNull("mes") ? in.get("mes").asInt() : null,
                texto(in, "poder", null), texto(in, "codIbge", null),
                comFaixa ? texto(in, "faixa", null) : null, texto(in, "busca", null));
    }

    /** Sem período informado, usa o mais recente importado. */
    private Filtro periodo(Filtro f) {
        if (f.ano() != null && f.mes() != null) {
            return f;
        }
        var ultimo = repo.ultimoPeriodo().orElseThrow(() -> new IllegalStateException("Ainda não há dados importados."));
        return new Filtro(f.ano() != null ? f.ano() : ultimo.ano(), f.mes() != null ? f.mes() : ultimo.mes(),
                f.poder(), f.codIbge(), f.faixa(), f.busca());
    }

    private static String texto(JsonNode in, String campo, String padrao) {
        JsonNode v = in.get(campo);
        return v == null || v.isNull() || v.asText().isBlank() ? padrao : v.asText();
    }
}
