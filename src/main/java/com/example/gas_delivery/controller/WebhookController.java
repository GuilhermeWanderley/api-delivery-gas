package com.example.gas_delivery.controller;

import com.example.gas_delivery.service.BotMessageProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final BotMessageProcessorService botMessageProcessorService;

    public WebhookController(BotMessageProcessorService botMessageProcessorService) {
        this.botMessageProcessorService = botMessageProcessorService;
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<Map<String, String>> receberMensagem(@RequestBody Map<String, Object> payload) {
        String telefone = extrairTelefone(payload);
        String mensagem = extrairMensagem(payload);

        String respostaBot = botMessageProcessorService.processarMensagem(telefone, mensagem);
        return ResponseEntity.ok(Map.of("status", "ok", "reply", respostaBot));
    }

    private String extrairTelefone(Map<String, Object> payload) {
        Object directNumber = payload.get("telefone");
        if (directNumber instanceof String value && !value.isBlank()) {
            return value;
        }

        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object key = dataMap.get("key");
            if (key instanceof Map<?, ?> keyMap) {
                Object remoteJid = keyMap.get("remoteJid");
                if (remoteJid instanceof String jid && !jid.isBlank()) {
                    return jid.split("@")[0];
                }
            }
        }

        throw new IllegalArgumentException("Telefone não encontrado no payload da Evolution.");
    }

    private String extrairMensagem(Map<String, Object> payload) {
        Object directText = payload.get("mensagem");
        if (directText instanceof String value) {
            return value;
        }

        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object message = dataMap.get("message");
            if (message instanceof Map<?, ?> messageMap) {
                Object conversation = messageMap.get("conversation");
                if (conversation instanceof String text) {
                    return text;
                }
            }
        }

        return "";
    }
}
