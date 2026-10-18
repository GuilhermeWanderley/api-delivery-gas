package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.EvolutionWebhookRequest;
import com.example.gas_delivery.service.EvolutionWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recebe os eventos da Evolution API.
 *
 * <p>Configurar na Evolution (Webhooks) apontando para
 * {@code http://host-do-backend:8080/api/webhook/evolution} e ativar o evento
 * {@code MESSAGES_UPSERT}. O header {@code X-Api-Key} é exigido quando
 * {@code BOT_API_KEY} estiver definido.
 */
@RestController
@RequestMapping("/api/webhook")
public class EvolutionWebhookController {

    private final EvolutionWebhookService service;

    public EvolutionWebhookController(EvolutionWebhookService service) {
        this.service = service;
    }

    @PostMapping("/evolution")
    public ResponseEntity<Map<String, String>> receber(@RequestBody(required = false) EvolutionWebhookRequest req) {
        try {
            service.processar(req);
        } catch (RuntimeException e) {
            // Nunca devolver erro: a Evolution reenviaria o evento em loop.
            return ResponseEntity.status(HttpStatus.OK).body(Map.of("status", "IGNORADO"));
        }
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
