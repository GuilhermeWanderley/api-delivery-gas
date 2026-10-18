package com.example.gas_delivery.entities;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;

public enum FormaPagamento {
    DINHEIRO,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    PIX;

    /**
     * Aceita tanto o nome do enum quanto rótulos amigáveis vindos do Typebot
     * (ex.: "Dinheiro", "Cartão de credito", "Cartão de debito", "PIX").
     */
    @JsonCreator
    public static FormaPagamento fromValue(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        for (FormaPagamento f : values()) {
            if (f.name().equalsIgnoreCase(v)) {
                return f;
            }
        }
        String n = normalize(v);
        return switch (n) {
            case "dinheiro" -> DINHEIRO;
            case "pix" -> PIX;
            case "cartaodecredito", "cartaocredito" -> CARTAO_CREDITO;
            case "cartaodedebito", "cartaodebito" -> CARTAO_DEBITO;
            default -> throw new IllegalArgumentException(
                    "Forma de pagamento inválida: '" + value
                            + "'. Valores aceitos: DINHEIRO, CARTAO_CREDITO, CARTAO_DEBITO, PIX.");
        };
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}
