package br.com.payrollda.lrf.chat;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.payrollda.lrf.auth.Usuario;
import br.com.payrollda.lrf.auth.UsuarioAtual;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;
    private final ChatRepository conversas;
    private final ObjectMapper json;

    public ChatController(ChatService chat, ChatRepository conversas, ObjectMapper json) {
        this.chat = chat;
        this.conversas = conversas;
        this.json = json;
    }

    public record Pedido(Long conversaId, List<ChatService.Mensagem> mensagens, ChatService.Contexto contexto) {
    }

    public record Resposta(long conversaId, String texto, List<ChatService.Sugestao> sugestoes) {
    }

    public record Mensagem(String papel, String texto, Object sugestoes) {
    }

    /** Responde e grava a pergunta e a resposta na conversa (cria uma nova quando conversaId vem vazio). */
    @PostMapping
    public Resposta conversar(@RequestBody Pedido p) throws Exception {
        if (p == null || p.mensagens() == null || p.mensagens().isEmpty()) {
            throw new IllegalArgumentException("Envie ao menos uma mensagem.");
        }
        long usuario = usuario().id();
        if (p.conversaId() != null && !conversas.ehDono(p.conversaId(), usuario)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        ChatService.Resposta r = chat.conversar(p.mensagens(), p.contexto());
        String pergunta = p.mensagens().get(p.mensagens().size() - 1).texto();
        long id = p.conversaId() != null ? p.conversaId() : conversas.criar(usuario, titulo(pergunta));
        conversas.adicionar(id, "user", pergunta, null);
        conversas.adicionar(id, "assistant", r.texto(), r.sugestoes().isEmpty() ? null : json.writeValueAsString(r.sugestoes()));
        return new Resposta(id, r.texto(), r.sugestoes());
    }

    @GetMapping("/conversas")
    public List<ChatRepository.Conversa> listar() {
        return conversas.listar(usuario().id());
    }

    @GetMapping("/conversas/{id}")
    public List<Mensagem> mensagens(@PathVariable long id) throws Exception {
        if (!conversas.ehDono(id, usuario().id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        List<Mensagem> lista = new java.util.ArrayList<>();
        for (ChatRepository.MensagemSalva m : conversas.mensagens(id)) {
            lista.add(new Mensagem(m.papel(), m.texto(), m.sugestoes() == null ? List.of() : json.readTree(m.sugestoes())));
        }
        return lista;
    }

    @DeleteMapping("/conversas/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        return conversas.remover(id, usuario().id()) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private static Usuario usuario() {
        Usuario u = UsuarioAtual.get();
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre no sistema.");
        }
        return u;
    }

    private static String titulo(String pergunta) {
        String t = pergunta.replaceAll("\\s+", " ").trim();
        return t.length() <= 80 ? t : t.substring(0, 77) + "…";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> indisponivel(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("mensagem", e.getMessage()));
    }
}
