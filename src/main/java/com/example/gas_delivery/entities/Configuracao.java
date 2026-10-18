package com.example.gas_delivery.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Configuração global de preços (linha única, id = 1), editável pelo painel.
 * O preço muda com frequência, então fica no banco em vez de fixo no código.
 */
@Entity
@Table(name = "configuracoes")
@Getter
@Setter
@NoArgsConstructor
public class Configuracao {

    public static final long ID_PADRAO = 1L;

    @Id
    private Long id = ID_PADRAO;

    /** Gás comum (P13) à vista (dinheiro, pix ou débito). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoGasVista = new BigDecimal("150.00");

    /** Gás comum (P13) no cartão de crédito (parcelável). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoGasCredito = new BigDecimal("155.00");

    /** Gás do Povo (programa do governo). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoGasPovo = new BigDecimal("20.00");

    /** Desconto aplicado toda quarta-feira (em reais). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal descontoQuarta = new BigDecimal("10.00");

    /** A promoção de quarta-feira está ativa? */
    @Column(nullable = false)
    private boolean promocaoQuartaAtiva = true;
}
