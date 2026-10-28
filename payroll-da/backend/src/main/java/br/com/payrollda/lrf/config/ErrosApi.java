package br.com.payrollda.lrf.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Erros que nenhum controller tratou: responde com {"mensagem": ...} para o painel mostrar
 * o motivo, em vez de só "A API respondeu 500".
 */
@RestControllerAdvice
public class ErrosApi {

    private static final Logger log = LoggerFactory.getLogger(ErrosApi.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensagem", String.valueOf(e.getReason())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> inesperado(Exception e) {
        log.error("Erro não tratado na API", e);
        String detalhe = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensagem", "Erro no servidor: " + detalhe));
    }
}
