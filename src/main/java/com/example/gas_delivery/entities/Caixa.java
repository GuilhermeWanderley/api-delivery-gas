package com.example.gas_delivery.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Um turno de caixa: o operador abre no início do dia e fecha no fim.
 * Ao fechar, os totais são "congelados" em {@link ResumoCaixaEntregador},
 * formando um relatório histórico que não muda depois.
 */
@Entity
@Table(name = "caixas")
@Getter
@Setter
@NoArgsConstructor
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataAbertura;

    private LocalDateTime dataFechamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCaixa status = StatusCaixa.ABERTO;

    /** Nome de quem abriu o caixa (opcional). */
    private String abertoPor;

    private String observacao;

    // ---- Totais congelados no fechamento ----

    @Column(precision = 10, scale = 2)
    private BigDecimal totalGeral = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalDinheiro = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCartao = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalPix = BigDecimal.ZERO;

    private Integer quantidadePedidos = 0;

    /** Resumo por entregador — o "relatório congelado" do turno. */
    @OneToMany(mappedBy = "caixa", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ResumoCaixaEntregador> resumo = new ArrayList<>();

    public boolean isAberto() {
        return status == StatusCaixa.ABERTO;
    }

    public void adicionarResumo(ResumoCaixaEntregador linha) {
        linha.setCaixa(this);
        this.resumo.add(linha);
    }
}
