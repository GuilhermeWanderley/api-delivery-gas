package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.OrigemPedido;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.entities.StatusPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.ClienteRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regras de criação de pedido compartilhadas entre o robô e o lançamento
 * manual. O foco aqui é o fluxo manual (novo) e o preço/status que os dois
 * fluxos precisam manter idênticos.
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private ConfiguracaoService configuracaoService;

    private PedidoService pedidoService;

    private static final BigDecimal VALOR_P13 = new BigDecimal("150.00");
    private static final BigDecimal VALOR_P13_CREDITO = new BigDecimal("155.00");
    private static final BigDecimal VALOR_GAS_DO_POVO = new BigDecimal("20.00");

    @BeforeEach
    void setUp() {
        lenient().when(configuracaoService.obter()).thenReturn(configPadrao());
        pedidoService = new PedidoService(
                clienteRepository, pedidoRepository, caixaRepository,
                new PrecoService(configuracaoService));
    }

    /** Configuração de teste com a promoção de quarta desligada (evita depender do dia). */
    private Configuracao configPadrao() {
        Configuracao cfg = new Configuracao();
        cfg.setPrecoGasVista(VALOR_P13);
        cfg.setPrecoGasCredito(VALOR_P13_CREDITO);
        cfg.setPrecoGasPovo(VALOR_GAS_DO_POVO);
        cfg.setDescontoQuarta(new BigDecimal("10.00"));
        cfg.setPromocaoQuartaAtiva(false);
        return cfg;
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

    private PedidoBotRequest criarRequestGasDoPovo() {
        PedidoBotRequest req = new PedidoBotRequest();
        req.setTelefone("5581999990000");
        req.setNome("Maria");
        req.setCpf("12345678901");
        req.setTipoPedido(TipoPedido.GAS_DO_POVO);
        req.setEnderecoEntrega("Rua B, 456, Olinda");
        req.setFormaPagamento(FormaPagamento.CARTAO_DEBITO);
        return req;
    }

    private void configurarMocksBasicos() {
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
    }

    private Caixa caixaAberto() {
        Caixa caixa = new Caixa();
        caixa.setId(7L);
        when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                .thenReturn(Optional.of(caixa));
        return caixa;
    }

    // ==================== Pedido manual ====================

    @Nested
    @DisplayName("Pedido manual (atendente)")
    class PedidoManualTests {

        @Test
        @DisplayName("Sem caixa aberto devolve conflito e não cria o pedido")
        void semCaixaAbertoDeveLancarConflito() {
            when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                    .thenReturn(Optional.empty());

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> pedidoService.criarPedidoManual(criarRequestNormal()));

            assertEquals("Abra o caixa antes de lançar um pedido.", ex.getMessage());
            verify(pedidoRepository, never()).save(any(Pedido.class));
            verify(clienteRepository, never()).save(any(Cliente.class));
        }

        @Test
        @DisplayName("Pedido manual nasce com origem MANUAL, PENDENTE e vinculado ao caixa aberto")
        void pedidoManualDeveNascerComOrigemManualEVinculadoAoCaixa() {
            configurarMocksBasicos();
            Caixa caixa = caixaAberto();

            Pedido resultado = pedidoService.criarPedidoManual(criarRequestNormal());

            assertEquals(OrigemPedido.MANUAL, resultado.getOrigem());
            assertEquals(Status.PENDENTE, resultado.getStatus());
            assertSame(caixa, resultado.getCaixa());
            assertEquals(VALOR_P13, resultado.getValorTotal());
        }

        @Test
        @DisplayName("PIX no pedido manual fica AGUARDANDO comprovante")
        void pixManualDeveFicarAguardando() {
            configurarMocksBasicos();
            caixaAberto();
            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(FormaPagamento.PIX);

            Pedido resultado = pedidoService.criarPedidoManual(request);

            assertEquals(StatusPagamento.AGUARDANDO, resultado.getStatusPagamento());
        }

        @Test
        @DisplayName("Dinheiro/cartão no pedido manual fica NAO_APLICAVEL")
        void pagamentoNaoPixManualDeveSerNaoAplicavel() {
            configurarMocksBasicos();
            caixaAberto();

            Pedido resultado = pedidoService.criarPedidoManual(criarRequestNormal());

            assertEquals(StatusPagamento.NAO_APLICAVEL, resultado.getStatusPagamento());
        }

        @Test
        @DisplayName("Pedido manual NORMAL no crédito custa 155")
        void manualNormalCreditoDeveCustar155() {
            configurarMocksBasicos();
            caixaAberto();
            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);

            Pedido resultado = pedidoService.criarPedidoManual(request);

            assertEquals(VALOR_P13_CREDITO, resultado.getValorTotal());
        }

        @Test
        @DisplayName("Pedido manual GAS_DO_POVO custa 20 e exige CPF")
        void manualGasDoPovoDeveCustar20EExigirCpf() {
            configurarMocksBasicos();
            caixaAberto();

            Pedido resultado = pedidoService.criarPedidoManual(criarRequestGasDoPovo());

            assertEquals(VALOR_GAS_DO_POVO, resultado.getValorTotal());
            assertEquals(Status.AGUARDANDO_VALIDACAO_CPF, resultado.getStatus());
            assertEquals(StatusPagamento.NAO_APLICAVEL, resultado.getStatusPagamento());
        }

        @Test
        @DisplayName("Pedido manual GAS_DO_POVO sem CPF é rejeitado")
        void manualGasDoPovoSemCpfDeveSerRejeitado() {
            caixaAberto();
            PedidoBotRequest request = criarRequestGasDoPovo();
            request.setCpf(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));

            assertEquals("CPF é obrigatório para pedidos do tipo Gás do Povo.", ex.getMessage());
            verify(pedidoRepository, never()).save(any(Pedido.class));
        }

        @Test
        @DisplayName("Pedido manual NORMAL não exige CPF")
        void manualNormalNaoExigeCpf() {
            configurarMocksBasicos();
            caixaAberto();

            assertDoesNotThrow(() -> pedidoService.criarPedidoManual(criarRequestNormal()));
        }
    }

    // ==================== Regras compartilhadas ====================

    @Nested
    @DisplayName("Criação compartilhada (robô e manual)")
    class CriacaoCompartilhadaTests {

        @Test
        @DisplayName("Fluxo do robô pode criar pedido sem caixa e com chave de idempotência")
        void fluxoDoRoboPodeCriarSemCaixa() {
            configurarMocksBasicos();

            Pedido resultado = pedidoService.criarPedido(
                    criarRequestNormal(), OrigemPedido.BOT, null, "chave-1");

            assertEquals(OrigemPedido.BOT, resultado.getOrigem());
            assertNull(resultado.getCaixa());
            assertEquals("chave-1", resultado.getChaveIdempotencia());
            assertEquals(VALOR_P13, resultado.getValorTotal());
        }

        @Test
        @DisplayName("GAS_DO_POVO normaliza o CPF salvo no cliente")
        void gasDoPovoDeveSalvarCpfNormalizado() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestGasDoPovo();
            request.setCpf("123.456.789-01");

            pedidoService.criarPedido(request, OrigemPedido.BOT, null, null);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            assertEquals("12345678901", captor.getValue().getCpf());
        }

        @Test
        @DisplayName("Deve criar novo cliente se o telefone não existir")
        void deveCriarNovoClienteSeNaoExistir() {
            configurarMocksBasicos();

            pedidoService.criarPedido(criarRequestNormal(), OrigemPedido.BOT, null, null);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            assertEquals("5581999990000", captor.getValue().getTelefone());
            assertEquals("Carlos", captor.getValue().getNome());
        }

        @Test
        @DisplayName("Deve atualizar cliente existente se o telefone já existir")
        void deveAtualizarClienteExistente() {
            Cliente existente = new Cliente();
            existente.setId(5L);
            existente.setTelefone("5581999990000");
            existente.setNome("Nome Antigo");

            when(clienteRepository.findByTelefone("5581999990000")).thenReturn(Optional.of(existente));
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

            pedidoService.criarPedido(criarRequestNormal(), OrigemPedido.BOT, null, null);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            assertEquals(5L, captor.getValue().getId());
            assertEquals("Carlos", captor.getValue().getNome());
        }

        @Test
        @DisplayName("Normaliza telefone sem prefixo 55")
        void deveNormalizarTelefoneSemPrefixo() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone("81999990000");

            pedidoService.criarPedido(request, OrigemPedido.BOT, null, null);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            assertEquals("5581999990000", captor.getValue().getTelefone());
        }
    }

    // ==================== Validações de entrada ====================

    @Nested
    @DisplayName("Validações de entrada")
    class ValidacaoTests {

        @Test
        @DisplayName("Telefone nulo é rejeitado")
        void telefoneNuloDeveSerRejeitado() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }

        @Test
        @DisplayName("Telefone em branco é rejeitado")
        void telefoneVazioDeveSerRejeitado() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone("  ");

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }

        @Test
        @DisplayName("Nome nulo é rejeitado")
        void nomeNuloDeveSerRejeitado() {
            PedidoBotRequest request = criarRequestNormal();
            request.setNome(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }

        @Test
        @DisplayName("Endereço em branco é rejeitado")
        void enderecoVazioDeveSerRejeitado() {
            PedidoBotRequest request = criarRequestNormal();
            request.setEnderecoEntrega("");

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }

        @Test
        @DisplayName("Forma de pagamento nula é rejeitada")
        void formaPagamentoNulaDeveSerRejeitada() {
            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }

        @Test
        @DisplayName("Tipo do pedido nulo é rejeitado")
        void tipoPedidoNuloDeveSerRejeitado() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTipoPedido(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoService.criarPedidoManual(request));
        }
    }
}
