package br.com.payrollda.lrf.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.payrollda.lrf.dashboard.DashboardDtos;
import br.com.payrollda.lrf.dashboard.DashboardDtos.EntidadeLinha;
import br.com.payrollda.lrf.dashboard.DashboardDtos.PontoEvolucao;
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
    // cada rodada reenvia instruções, ferramentas, histórico e saídas anteriores; no plano gratuito do Groq
    // (limite de tokens por minuto) poucas rodadas já estouram a cota
    private static final int MAX_RODADAS = 4;
    private static final int MAX_HISTORICO = 8;
    private static final int MAX_CARACTERES_HISTORICO = 1200;
    /** Espera máxima por tentativa quando o Groq responde 429; acima disso devolve a mensagem ao usuário. */
    private static final double MAX_ESPERA_SEGUNDOS = 20;
    private static final int MAX_TENTATIVAS_429 = 2;
    private static final Pattern TEMPO_SUGERIDO = Pattern.compile("try again in (?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:([\\d.]+)(ms|s))?");

    static final String INSTRUCOES = """
            Você é o assistente do Payroll DA: despesa com pessoal das prefeituras e câmaras do Paraná, pelo RGF do TCE-PR.

            LRF (percentual da RCL ajustada; Executivo / Legislativo municipal):
            - Máximo 54% / 6%. Acima: eliminar o excedente em dois quadrimestres, ao menos 1/3 no primeiro; ex.: cortar
              20% ou mais de cargos em comissão e funções de confiança, exonerar não estáveis (art. 23; CF art. 169).
            - Prudencial (95%): 51,3% / 5,7%. Vedados reajuste (salvo revisão geral), criação de cargos, mudança de
              carreira que aumente despesa, admissões (salvo reposição em educação, saúde e segurança) e hora extra (art. 22).
            - Alerta (90%): 48,6% / 5,4%; o Tribunal de Contas alerta o ente (art. 59, §1º, II).

            Como trabalhar:
            - Todo número vem das ferramentas; nunca invente valores, nomes ou percentuais. Chame só as necessárias.
            - Responda em português, curto (até uns 6 parágrafos ou listas). Valores como R$ 1,2 mi e 51,3%.
            - Sobre uma entidade: situação, tendência recente, margem em reais até o limite e medidas proporcionais à
              faixa, como sugestões, não parecer jurídico.
            - Se um filtro ou entidade no painel ajudar, chame sugerir_ajuste (até 3). O usuário vê um botão "Aplicar";
              não diga que mudou a tela.
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
        // mantém só as últimas mensagens, e as antigas encurtadas, para caber no limite de tokens por minuto
        List<Mensagem> recentes = historico.subList(Math.max(0, historico.size() - MAX_HISTORICO), historico.size());
        for (int i = 0; i < recentes.size(); i++) {
            Mensagem m = recentes.get(i);
            String papel = "assistant".equals(m.papel()) ? "assistant" : "user";
            String conteudo = m.texto() == null ? "" : m.texto();
            if (i < recentes.size() - 1 && conteudo.length() > MAX_CARACTERES_HISTORICO) {
                conteudo = conteudo.substring(0, MAX_CARACTERES_HISTORICO) + "…";
            }
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
        for (int tentativa = 0; ; tentativa++) {
            try {
                return chamarApiUmaVez(mensagens);
            } catch (LimiteExcedido e) {
                double espera = e.esperaSegundos;
                if (tentativa >= MAX_TENTATIVAS_429 || espera < 0 || espera > MAX_ESPERA_SEGUNDOS) {
                    throw new IllegalStateException(mensagemLimite(e.detalhe, espera));
                }
                log.info("Groq respondeu 429; aguardando {}s antes de tentar de novo.", espera);
                try {
                    Thread.sleep((long) (espera * 1000) + 500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(mensagemLimite(e.detalhe, espera));
                }
            }
        }
    }

    private JsonNode chamarApiUmaVez(List<Map<String, Object>> mensagens) {
        try {
            return enviar(mensagens);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                throw limite(e);
            }
            // o Groq aposenta modelos de tempos em tempos; se o configurado sumiu, troca por um disponível e tenta de novo
            if (e.getStatusCode().value() == 404 && trocarModelo()) {
                try {
                    return enviar(mensagens);
                } catch (RestClientResponseException e2) {
                    throw e2.getStatusCode().value() == 429 ? limite(e2) : falha(e2);
                }
            }
            throw falha(e);
        }
    }

    private JsonNode enviar(List<Map<String, Object>> mensagens) {
        Map<String, Object> corpo = new java.util.HashMap<>(Map.of(
                "model", modelo,
                "max_tokens", maxTokens,
                "temperature", 0.3,
                "tools", Ferramentas.DEFINICOES,
                "messages", mensagens));
        if (modelo.contains("gpt-oss")) {
            // modelos de raciocínio: o raciocínio também conta no limite de tokens por minuto
            corpo.put("reasoning_effort", "low");
        }
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

    /** 429 do Groq, com o tempo de espera sugerido (-1 se não veio). */
    static final class LimiteExcedido extends RuntimeException {
        final double esperaSegundos;
        final String detalhe;

        LimiteExcedido(double esperaSegundos, String detalhe) {
            super(detalhe);
            this.esperaSegundos = esperaSegundos;
            this.detalhe = detalhe;
        }
    }

    private LimiteExcedido limite(RestClientResponseException e) {
        String detalhe = "";
        try {
            detalhe = json.readTree(e.getResponseBodyAsString()).path("error").path("message").asText("");
        } catch (Exception ignorado) {
            // corpo sem JSON
        }
        log.warn("Groq atingiu o limite de uso: {}", detalhe);
        String cabecalho = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("retry-after");
        return new LimiteExcedido(esperaSugerida(cabecalho, detalhe), detalhe);
    }

    /** Segundos de espera pelo cabeçalho retry-after ou pelo texto "try again in 1m2.5s"; -1 se não houver. */
    static double esperaSugerida(String retryAfter, String detalhe) {
        if (retryAfter != null) {
            try {
                return Double.parseDouble(retryAfter.trim());
            } catch (NumberFormatException ignorado) {
                // formato de data; tenta pelo texto
            }
        }
        if (detalhe != null) {
            Matcher m = TEMPO_SUGERIDO.matcher(detalhe);
            if (m.find() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
                double s = 0;
                if (m.group(1) != null) {
                    s += Integer.parseInt(m.group(1)) * 3600;
                }
                if (m.group(2) != null) {
                    s += Integer.parseInt(m.group(2)) * 60;
                }
                if (m.group(3) != null) {
                    double v = Double.parseDouble(m.group(3));
                    s += "ms".equals(m.group(4)) ? v / 1000 : v;
                }
                return s;
            }
        }
        return -1;
    }

    static String mensagemLimite(String detalhe, double espera) {
        boolean diario = detalhe != null && detalhe.contains("per day");
        String quando;
        if (espera < 0) {
            quando = "Aguarde um pouco";
        } else if (espera < 90) {
            quando = "Aguarde cerca de " + Math.max(1, (long) Math.ceil(espera)) + " segundos";
        } else {
            quando = "Aguarde cerca de " + (long) Math.ceil(espera / 60) + " minutos";
        }
        return diario
                ? "O assistente atingiu o limite diário de uso do plano gratuito da IA. " + quando
                        + " e tente de novo, ou configure um modelo com cota maior."
                : "O assistente recebeu muitas perguntas em pouco tempo e atingiu o limite de uso por minuto do plano "
                        + "gratuito da IA. " + quando + " e envie a pergunta de novo.";
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
                int limite = Math.min(Math.max(in.path("limite").asInt(8), 1), 20);
                yield tabelaEntidades(repo.entidades(periodo(filtro(in, true)),
                        texto(in, "ordem", "usoLimite"), texto(in, "direcao", "desc"), 0, limite));
            }
            case "evolucao_entidade" -> {
                var serie = repo.evolucaoEntidade(in.path("entidadeId").asLong());
                yield tabelaEvolucao(serie.subList(Math.max(0, serie.size() - 12), serie.size()));
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

    // saídas em tabela (cabeçalho + linhas separadas por ;) em vez de JSON: repetir as chaves em cada linha
    // gastava a maior parte dos tokens das rodadas seguintes

    static String tabelaEntidades(DashboardDtos.Pagina<EntidadeLinha> p) {
        StringBuilder sb = new StringBuilder("total=").append(p.total()).append(" mostrando=").append(p.itens().size())
                .append("\nid;municipio;entidade;poder;despesa_rs;rcl_rs;pct_rcl;limite_max_pct;faixa");
        for (EntidadeLinha e : p.itens()) {
            sb.append('\n').append(e.entidadeId()).append(';').append(e.municipio()).append(';').append(e.entidade())
                    .append(';').append(e.poder()).append(';').append(inteiro(e.despesaTotal())).append(';')
                    .append(inteiro(e.rclAjustada())).append(';').append(decimal(e.percentual())).append(';')
                    .append(decimal(e.limiteMaximo())).append(';').append(e.faixa());
        }
        return sb.toString();
    }

    static String tabelaEvolucao(List<PontoEvolucao> serie) {
        StringBuilder sb = new StringBuilder("ano;mes;despesa_rs;rcl_rs;pct_rcl;limite_max_pct");
        for (PontoEvolucao p : serie) {
            sb.append('\n').append(p.ano()).append(';').append(p.mes()).append(';').append(inteiro(p.despesaTotal()))
                    .append(';').append(inteiro(p.rclAjustada())).append(';').append(decimal(p.percentual()))
                    .append(';').append(decimal(p.limiteMaximo()));
        }
        return sb.toString();
    }

    private static String inteiro(BigDecimal v) {
        return v == null ? "" : v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String decimal(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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
