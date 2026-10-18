package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Preço compartilhado pelo robô e pelo pedido manual. */
@ExtendWith(MockitoExtension.class)
class PrecoServiceTest {

    @Mock
    private ConfiguracaoService configuracaoService;

    private PrecoService precoService;

    private Configuracao config;

    @BeforeEach
    void setUp() {
        config = new Configuracao();
        config.setPrecoGasVista(new BigDecimal("150.00"));
        config.setPrecoGasCredito(new BigDecimal("155.00"));
        config.setPrecoGasPovo(new BigDecimal("20.00"));
        config.setDescontoQuarta(new BigDecimal("10.00"));
        config.setPromocaoQuartaAtiva(true);

        precoService = new PrecoService(configuracaoService);
    }

    @Test
    @DisplayName("Gás do Povo custa 20 em qualquer dia")
    void gasDoPovoCusta20() {
        when(configuracaoService.obter()).thenReturn(config);

        assertEquals(new BigDecimal("20.00"),
                precoService.calcularValorTotal(
                        TipoPedido.GAS_DO_POVO, FormaPagamento.PIX, LocalDate.of(2026, 10, 21)));
    }

    @Test
    @DisplayName("Gás normal à vista custa 150 fora da quarta")
    void normalVistaCusta150() {
        when(configuracaoService.obter()).thenReturn(config);

        assertEquals(new BigDecimal("150.00"),
                precoService.calcularValorTotal(
                        TipoPedido.NORMAL, FormaPagamento.DINHEIRO, LocalDate.of(2026, 10, 19)));
    }

    @Test
    @DisplayName("Gás normal no crédito custa 155 fora da quarta")
    void normalCreditoCusta155() {
        when(configuracaoService.obter()).thenReturn(config);

        assertEquals(new BigDecimal("155.00"),
                precoService.calcularValorTotal(
                        TipoPedido.NORMAL, FormaPagamento.CARTAO_CREDITO, LocalDate.of(2026, 10, 19)));
    }

    @Test
    @DisplayName("Na quarta com promoção ativa: 140 à vista e 145 no crédito")
    void quartaComPromocaoAplicaDesconto() {
        when(configuracaoService.obter()).thenReturn(config);
        LocalDate quarta = LocalDate.of(2026, 10, 21);

        assertTrue(precoService.ehQuartaFeira(quarta));
        assertEquals(new BigDecimal("140.00"),
                precoService.calcularValorTotal(TipoPedido.NORMAL, FormaPagamento.PIX, quarta));
        assertEquals(new BigDecimal("145.00"),
                precoService.calcularValorTotal(TipoPedido.NORMAL, FormaPagamento.CARTAO_CREDITO, quarta));
    }

    @Test
    @DisplayName("Promoção desligada mantém 150/155 mesmo na quarta")
    void promocaoDesligadaNaoAplicaDesconto() {
        config.setPromocaoQuartaAtiva(false);
        when(configuracaoService.obter()).thenReturn(config);
        LocalDate quarta = LocalDate.of(2026, 10, 21);

        assertEquals(new BigDecimal("150.00"),
                precoService.calcularValorTotal(TipoPedido.NORMAL, FormaPagamento.DINHEIRO, quarta));
        assertEquals(new BigDecimal("155.00"),
                precoService.calcularValorTotal(TipoPedido.NORMAL, FormaPagamento.CARTAO_CREDITO, quarta));
    }

    @Test
    @DisplayName("ehQuartaFeira trata nulo e outros dias")
    void ehQuartaFeiraTrataNuloEOutrosDias() {
        assertFalse(precoService.ehQuartaFeira(null));
        assertFalse(precoService.ehQuartaFeira(LocalDate.of(2026, 10, 19)));
    }
}
