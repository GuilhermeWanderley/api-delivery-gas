package com.example.gas_delivery.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pedidos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pedido_chave_idempotencia",
                columnNames = "chave_idempotencia"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPedido tipoPedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal precisaTrocoPara;

    @Column(nullable = false)
    private String enderecoEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    @Column(nullable = false)
    private LocalDateTime dataAtualizacao;

    @ManyToOne
    @JoinColumn(name = "entregador_id")
    private Entregador entregador;

    /**
     * Chave de idempotência informada pelo robô. A restrição única garante que
     * a mesma requisição reenviada nunca gere dois pedidos.
     */
    @Column(name = "chave_idempotencia", length = 128)
    private String chaveIdempotencia;

    /** Situação do PIX/dinheiro/cartão — permite rastrear estorno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", length = 30)
    private StatusPagamento statusPagamento = StatusPagamento.NAO_APLICAVEL;

    /** Veio do robô ou foi lançado pelo atendente. */
    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 10)
    private OrigemPedido origem = OrigemPedido.BOT;

    /**
     * Turno de caixa em que o pedido foi criado. O vínculo é por chave
     * estrangeira (e não por janela de data/hora): pedidos antigos ficam com
     * {@code caixa_id} nulo de propósito e não aparecem no turno atual.
     * <p>
     * Não é serializado no JSON: o perfil docker roda com
     * {@code spring.jpa.open-in-view=false}, então tocar em uma referência
     * {@link FetchType#LAZY} durante a serialização estouraria
     * {@code LazyInitializationException}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id")
    @JsonIgnore
    private Caixa caixa;
}