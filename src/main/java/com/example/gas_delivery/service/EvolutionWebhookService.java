package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.EvolutionWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Processa os eventos enviados pela Evolution API pelo webhook.
 *
 * <p>Hoje o único tratamento é o <b>áudio</b>: o robô do Typebot não interpreta
 * mensagem de voz, então ela viraria silêncio. Ao detectar áudio, criamos um
 * alerta de atendimento humano (transbordo) para o painel.
 */
@Service
public class EvolutionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookService.class);

    private final TransbordoService transbordoService;

    public EvolutionWebhookService(TransbordoService transbordoService) {
        this.transbordoService = transbordoService;
    }

    public void processar(EvolutionWebhookRequest req) {
        if (req == null) {
            return;
        }

        String evento = req.getEvent() == null ? "" : req.getEvent().toLowerCase();
        if (!evento.contains("messages.upsert")) {
            return;
        }

        Map<String, Object> data = req.getData();
        if (data == null) {
            return;
        }

        Map<String, Object> key = comoMapa(data.get("key"));
        if (key == null) {
            return;
        }

        // Mensagens enviadas por nós mesmos não são pedido de atendimento.
        if (Boolean.TRUE.equals(key.get("fromMe"))) {
            return;
        }

        String jid = comoTexto(key.get("remoteJid"));
        if (jid == null || jid.isBlank() || jid.contains("@g.us")) {
            return;
        }

        Map<String, Object> mensagem = comoMapa(data.get("message"));
        if (mensagem == null) {
            return;
        }

        if (contemAudio(mensagem)) {
            String nome = comoTexto(data.get("pushName"));
            log.info("[WEBHOOK] áudio recebido de {} — gerando alerta de atendimento humano", jid);
            transbordoService.solicitar(jid, nome, TransbordoService.MOTIVO_AUDIO);
        }
    }

    private boolean contemAudio(Map<String, Object> mensagem) {
        if (mensagem.containsKey("audioMessage")) {
            return true;
        }
        Object ptt = mensagem.get("ptt");
        if (ptt instanceof Map<?, ?> mapaPtt && Boolean.TRUE.equals(mapaPtt.get("ptt"))) {
            return true;
        }
        // Alguns payloads trazem o conteúdo dentro de documentWithCaption/ephemeral.
        for (Object valor : mensagem.values()) {
            if (valor instanceof Map<?, ?> interno && interno.containsKey("audioMessage")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> comoMapa(Object valor) {
        return valor instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : null;
    }

    private String comoTexto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
