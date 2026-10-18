package com.example.gas_delivery.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Linha do relatório congelado de um caixa: quanto cada entregador
 * movimentou no turno, separado por forma de pagamento.
 */
@Entity
@Table(name = "caixa_resumo_entregador")
@Getter
@Setter
@NoArgsConstructor
public class ResumoCaixaEntregador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JsonBackReference
    private Caixa caixa;

    private Long entregadorId;

    @Column(nullable = false)
    private String entregadorNome;

    private Integer quantidadePedidos = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalDinheiro = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCartao = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalPix = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
}
