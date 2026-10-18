package com.example.gas_delivery.entities;

/**
 * Situação do pagamento do pedido — permite rastrear estornos de PIX.
 *
 * <p>Fluxo típico do PIX:
 * {@code AGUARDANDO} → {@code COMPROVANTE_ENVIADO} → {@code CONFIRMADO}.
 * Se o pedido for cancelado depois de {@code CONFIRMADO}, passa a
 * {@code A_ESTORNAR} até o atendente devolver e marcar {@code ESTORNADO}.
 */
public enum StatusPagamento {
    /** Dinheiro/cartão na entrega — não há pix a controlar. */
    NAO_APLICAVEL,
    /** Cliente escolheu PIX; aguardando o comprovante. */
    AGUARDANDO,
    /** Comprovante recebido, ainda não conferido. */
    COMPROVANTE_ENVIADO,
    /** Atendente conferiu e o dinheiro entrou. */
    CONFIRMADO,
    /** Pedido cancelado depois do pagamento → precisa devolver. */
    A_ESTORNAR,
    /** Estorno realizado. */
    ESTORNADO
}
