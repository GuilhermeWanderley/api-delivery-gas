package com.example.gas_delivery.dto;

import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PedidoBotRequest {

    @NotBlank(message = "Telefone é obrigatório.")
    private String telefone;

    @NotBlank(message = "Nome é obrigatório.")
    private String nome;

    private String cpf;

    @NotNull(message = "Tipo do pedido é obrigatório.")
    private TipoPedido tipoPedido;

    @NotBlank(message = "Endereço de entrega é obrigatório.")
    private String enderecoEntrega;

    @NotNull(message = "Forma de pagamento é obrigatória.")
    private FormaPagamento formaPagamento;

    /**
     * Troco desejado. Aceita {@code ""}, {@code "0"}, {@code "50"}, {@code "R$ 50,00"}.
     * Valores ilegíveis viram {@code null} — nunca estouram a requisição.
     */
    @JsonDeserialize(using = MoneyDeserializer.class)
    private BigDecimal precisaTrocoPara;

    /**
     * Chave de idempotência (opcional). Se o Typebot reenviar a mesma
     * requisição — retry, duplo clique ou reconexão — o pedido <b>não</b> é
     * criado duas vezes: devolvemos o pedido que já existe.
     */
    private String idempotencyKey;
}
