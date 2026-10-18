package com.example.gas_delivery.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Lê valores monetários "sujos" vindos do WhatsApp sem derrubar a requisição.
 *
 * <p>Aceita {@code 50}, {@code "50"}, {@code "50,00"}, {@code "R$ 50,00"},
 * {@code ""} e {@code "0"} — este último vira {@link BigDecimal#ZERO}.
 * Qualquer coisa ilegível vira {@code null} em vez de exceção.
 */
public class MoneyDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT
                || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getDecimalValue();
        }

        String texto = parser.getValueAsString();
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String limpo = texto.replaceAll("[^0-9,.-]", "").trim();
        if (limpo.isEmpty() || limpo.equals(".") || limpo.equals("-")) {
            return null;
        }
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        if (limpo.startsWith(".")) {
            limpo = "0" + limpo;
        }

        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
