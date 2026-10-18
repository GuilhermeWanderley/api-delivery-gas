package com.example.gas_delivery.entities;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;

public enum TipoPedido {
    NORMAL,
    GAS_DO_POVO;

    /**
     * Aceita tanto o nome do enum quanto rótulos amigáveis vindos do Typebot
     * (ex.: "Gás normal", "Gás do povo", "NORMAL", "GAS_DO_POVO").
     */
    @JsonCreator
    public static TipoPedido fromValue(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        for (TipoPedido t : values()) {
            if (t.name().equalsIgnoreCase(v)) {
                return t;
            }
        }
        String n = normalize(v);
        return switch (n) {
            case "normal", "gasnormal" -> NORMAL;
            case "gasdopovo", "gaspovo" -> GAS_DO_POVO;
            default -> throw new IllegalArgumentException(
                    "Tipo de pedido inválido: '" + value + "'. Valores aceitos: NORMAL, GAS_DO_POVO.");
        };
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}
