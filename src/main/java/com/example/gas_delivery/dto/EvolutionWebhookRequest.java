package com.example.gas_delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Payload cru enviado pela Evolution API para o webhook da aplicação.
 * Só usamos o necessário: evento, instância, chave da mensagem e o conteúdo.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionWebhookRequest {

    private String event;
    private String instance;
    private String instanceName;
    private Map<String, Object> data;
}
