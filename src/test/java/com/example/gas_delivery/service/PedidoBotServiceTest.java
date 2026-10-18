package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.OrigemPedido;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Particularidades do fluxo do robô: idempotência, vínculo opcional com o
 * caixa e envio automático da chave PIX. A criação em si é testada em
 * {@link PedidoServiceTest}, pois é compartilhada com o pedido manual.
 */
@ExtendWith(MockitoExtension.class)
class PedidoBotServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private ConfiguracaoService configuracaoService;

    @Mock
    private WhatsappNotificationService whatsappNotificationService;

    @Mock
    private PedidoService pedidoService;

    private PedidoBotService pedidoBotService;

    @BeforeEach
    void setUp() {
        pedidoBotService = new PedidoBotService(
                pedidoRepository, caixaRepository, configuracaoService,
                whatsappNotificationService, pedidoService);
    }

    private PedidoBotRequest criarRequestNormal() {
        PedidoBotRequest req = new PedidoBotRequest();
        req.setTelefone("5581999990000");
        req.setNome("Carlos");
        req.setTipoPedido(TipoPedido.NORMAL);
        req.setEnderecoEntrega("Rua A, 123, Recife");
        req.setFormaPagamento(FormaPagamento.DINHEIRO);
        return req;
    }

    private Pedido pedidoCriado(FormaPagamento forma, TipoPedido tipo) {
        Pedido pedido = new Pedido();
        pedido.setId(100L);
        pedido.setFormaPagamento(forma);
        pedido.setTipoPedido(tipo);
        return pedido;
    }

    // ==================== Idempotência ====================

    @Nested
    @DisplayName("Idempotência das requisições do robô")
    class IdempotenciaTests {

        @Test
        @DisplayName("Chave repetida devolve o pedido existente e NÃO cria outro")
        void chaveRepetidaNaoDeveDuplicarPedido() {
            Pedido existente = new Pedido();
            existente.setId(99L);
            existente.setChaveIdempotencia("abc-123");

            when(pedidoService.normalizarChaveIdempotencia("abc-123")).thenReturn("abc-123");
            when(pedidoRepository.findByChaveIdempotencia("abc-123"))
                    .thenReturn(Optional.of(existente));

            PedidoBotRequest request = criarRequestNormal();
            request.setIdempotencyKey("abc-123");

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertSame(existente, resultado);
            verify(pedidoService, never()).criarPedido(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Chave nova é repassada para a criação do pedido")
        void chaveNovaDeveIrParaCriacao() {
            when(pedidoService.normalizarChaveIdempotencia("nova-chave")).thenReturn("nova-chave");
            when(pedidoRepository.findByChaveIdempotencia("nova-chave")).thenReturn(Optional.empty());
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());
            when(pedidoService.criarPedido(any(), any(), any(), any()))
                    .thenReturn(pedidoCriado(FormaPagamento.DINHEIRO, TipoPedido.NORMAL));

            PedidoBotRequest request = criarRequestNormal();
            request.setIdempotencyKey("nova-chave");

            pedidoBotService.criarPedidoViaBot(request);

            verify(pedidoService).criarPedido(
                    eq(request), eq(OrigemPedido.BOT), isNull(), eq("nova-chave"));
        }

        @Test
        @DisplayName("Sem chave informada, a criação recebe chave nula")
        void semChaveDeveCriarNormalmente() {
            when(pedidoService.normalizarChaveIdempotencia(null)).thenReturn(null);
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());
            when(pedidoService.criarPedido(any(), any(), any(), any()))
                    .thenReturn(pedidoCriado(FormaPagamento.DINHEIRO, TipoPedido.NORMAL));

            pedidoBotService.criarPedidoViaBot(criarRequestNormal());

            verify(pedidoService).criarPedido(
                    any(), eq(OrigemPedido.BOT), isNull(), isNull());
            verify(pedidoRepository, never()).findByChaveIdempotencia(any());
        }
    }

    // ==================== Caixa e PIX ====================

    @Nested
    @DisplayName("Caixa e notificação PIX")
    class CaixaEPixTests {

        @Test
        @DisplayName("Pedido do robô é vinculado ao caixa aberto quando existe")
        void deveVincularAoCaixaAberto() {
            Caixa caixa = new Caixa();
            caixa.setId(7L);

            when(pedidoService.normalizarChaveIdempotencia(null)).thenReturn(null);
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.of(caixa));
            when(pedidoService.criarPedido(any(), any(), any(), any()))
                    .thenReturn(pedidoCriado(FormaPagamento.DINHEIRO, TipoPedido.NORMAL));

            PedidoBotRequest request = criarRequestNormal();
            pedidoBotService.criarPedidoViaBot(request);

            verify(pedidoService).criarPedido(
                    eq(request), eq(OrigemPedido.BOT), eq(caixa), isNull());
        }

        @Test
        @DisplayName("PIX de pedido NORMAL envia a chave PIX na hora")
        void pixNormalDeveEnviarChave() {
            Configuracao cfg = new Configuracao();
            cfg.setChavePix("pix@empresa.com");
            Pedido pedido = pedidoCriado(FormaPagamento.PIX, TipoPedido.NORMAL);

            when(pedidoService.normalizarChaveIdempotencia(null)).thenReturn(null);
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());
            when(pedidoService.criarPedido(any(), any(), any(), any())).thenReturn(pedido);
            when(configuracaoService.obter()).thenReturn(cfg);

            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(FormaPagamento.PIX);
            pedidoBotService.criarPedidoViaBot(request);

            verify(whatsappNotificationService).enviarChavePix(pedido, "pix@empresa.com");
        }

        @Test
        @DisplayName("GAS_DO_POVO nunca recebe a chave PIX antes da aprovação do CPF")
        void gasDoPovoNaoDeveEnviarChave() {
            when(pedidoService.normalizarChaveIdempotencia(null)).thenReturn(null);
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());
            when(pedidoService.criarPedido(any(), any(), any(), any()))
                    .thenReturn(pedidoCriado(FormaPagamento.PIX, TipoPedido.GAS_DO_POVO));

            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(FormaPagamento.PIX);
            request.setTipoPedido(TipoPedido.GAS_DO_POVO);
            pedidoBotService.criarPedidoViaBot(request);

            verify(whatsappNotificationService, never()).enviarChavePix(any(), any());
        }

        @Test
        @DisplayName("Falha no envio do PIX não impede a criação do pedido")
        void falhaNoEnvioNaoDeveDerrubarPedido() {
            Configuracao cfg = new Configuracao();
            cfg.setChavePix("pix@empresa.com");
            Pedido pedido = pedidoCriado(FormaPagamento.PIX, TipoPedido.NORMAL);

            when(pedidoService.normalizarChaveIdempotencia(null)).thenReturn(null);
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());
            when(pedidoService.criarPedido(any(), any(), any(), any())).thenReturn(pedido);
            when(configuracaoService.obter()).thenReturn(cfg);
            doThrow(new RuntimeException("WhatsApp fora do ar"))
                    .when(whatsappNotificationService).enviarChavePix(any(), any());

            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(FormaPagamento.PIX);

            Pedido resultado = assertDoesNotThrow(
                    () -> pedidoBotService.criarPedidoViaBot(request));

            assertSame(pedido, resultado);
        }
    }
}
