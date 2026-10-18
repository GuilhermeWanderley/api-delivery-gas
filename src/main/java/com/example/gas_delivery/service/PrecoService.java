package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Cálculo de preço compartilhado por todos os fluxos de criação de pedido
 * (robô e lançamento manual pelo atendente). A tabela de preços fica na
 * {@link Configuracao}, editável pelo painel.
 *
 * <ul>
 *   <li><b>Gás do Povo</b>: valor fixo do programa;</li>
 *   <li><b>Gás comum</b>: preço à vista ou no crédito, com desconto
 *       automático de quarta-feira quando a promoção está ativa.</li>
 * </ul>
 */
@Service
public class PrecoService {

    private final ConfiguracaoService configuracaoService;

    public PrecoService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /** Preço para hoje. */
    public BigDecimal calcularValorTotal(TipoPedido tipo, FormaPagamento forma) {
        return calcularValorTotal(tipo, forma, LocalDate.now());
    }

    /**
     * Preço para uma data específica (permite testar a promoção de quarta-feira
     * sem depender do dia em que a suíte roda).
     */
    public BigDecimal calcularValorTotal(TipoPedido tipo, FormaPagamento forma, LocalDate data) {
        Configuracao cfg = configuracaoService.obter();
        if (tipo == TipoPedido.GAS_DO_POVO) {
            return cfg.getPrecoGasPovo();
        }
        BigDecimal base = forma == FormaPagamento.CARTAO_CREDITO
                ? cfg.getPrecoGasCredito()
                : cfg.getPrecoGasVista();
        if (ehQuartaFeira(data) && cfg.isPromocaoQuartaAtiva()) {
            base = base.subtract(cfg.getDescontoQuarta());
        }
        return base;
    }

    /** Verifica se a data cai em uma quarta-feira (dia da promoção). */
    public boolean ehQuartaFeira(LocalDate data) {
        return data != null && data.getDayOfWeek() == DayOfWeek.WEDNESDAY;
    }
}
