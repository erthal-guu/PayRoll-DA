package br.com.payrollda.lrf.chat;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record Pedido(List<ChatService.Mensagem> mensagens, ChatService.Contexto contexto) {
    }

    @PostMapping
    public ChatService.Resposta conversar(@RequestBody Pedido p) {
        if (p == null || p.mensagens() == null || p.mensagens().isEmpty()) {
            throw new IllegalArgumentException("Envie ao menos uma mensagem.");
        }
        return chat.conversar(p.mensagens(), p.contexto());
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
