package com.example.gas_delivery.service;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.Configuracao;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.TipoPedido;
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
import static org.mockito.Mockito.*;

/**
 * Testes unitários para o PedidoBotService — responsável por receber
 * pedidos finalizados do Typebot e persistir no banco.
 */
@ExtendWith(MockitoExtension.class)
class PedidoBotServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ConfiguracaoService configuracaoService;

    private PedidoBotService pedidoBotService;

    private static final BigDecimal VALOR_P13 = new BigDecimal("150.00");
    private static final BigDecimal VALOR_P13_CREDITO = new BigDecimal("155.00");
    private static final BigDecimal VALOR_GAS_DO_POVO = new BigDecimal("20.00");

    @BeforeEach
    void setUp() {
        lenient().when(configuracaoService.obter()).thenReturn(configPadrao());
        pedidoBotService = new PedidoBotService(
                clienteRepository, pedidoRepository, configuracaoService);
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

    // ==================== Regras do Gás do Povo ====================

    @Nested
    @DisplayName("Regra do Gás do Povo")
    class GasDoPovoTests {

        @Test
        @DisplayName("Pedido GAS_DO_POVO deve nascer com status AGUARDANDO_VALIDACAO_CPF")
        void gasDoPovoDeveNascerComStatusAguardandoValidacaoCpf() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestGasDoPovo();

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertEquals(Status.AGUARDANDO_VALIDACAO_CPF, resultado.getStatus());
            assertEquals(TipoPedido.GAS_DO_POVO, resultado.getTipoPedido());
        }

        @Test
        @DisplayName("Pedido GAS_DO_POVO deve ter valor de R$ 20,00")
        void gasDoPovoDeveTerValorCorreto() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestGasDoPovo();

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertEquals(VALOR_GAS_DO_POVO, resultado.getValorTotal());
        }

        @Test
        @DisplayName("Pedido GAS_DO_POVO sem CPF deve lançar exceção")
        void gasDoPovoSemCpfDeveLancarExcecao() {
            PedidoBotRequest request = criarRequestGasDoPovo();
            request.setCpf(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Pedido GAS_DO_POVO com CPF em branco deve lançar exceção")
        void gasDoPovoComCpfEmBrancoDeveLancarExcecao() {
            PedidoBotRequest request = criarRequestGasDoPovo();
            request.setCpf("   ");

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Pedido GAS_DO_POVO deve salvar o CPF no cliente")
        void gasDoPovoDeveSalvarCpfNoCliente() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestGasDoPovo();

            pedidoBotService.criarPedidoViaBot(request);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            assertEquals("12345678901", captor.getValue().getCpf());
        }
    }

    // ==================== Regras do Pedido Normal ====================

    @Nested
    @DisplayName("Regra do Pedido Normal")
    class PedidoNormalTests {

        @Test
        @DisplayName("Pedido NORMAL deve nascer com status PENDENTE")
        void normalDeveNascerComStatusPendente() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertEquals(Status.PENDENTE, resultado.getStatus());
            assertEquals(TipoPedido.NORMAL, resultado.getTipoPedido());
        }

        @Test
        @DisplayName("Pedido NORMAL deve ter valor de R$ 120,00 (P13)")
        void normalDeveTerValorP13() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertEquals(VALOR_P13, resultado.getValorTotal());
        }

        @Test
        @DisplayName("Pedido NORMAL não exige CPF")
        void normalNaoExigeCpf() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();
            request.setCpf(null);

            assertDoesNotThrow(() -> pedidoBotService.criarPedidoViaBot(request));
        }
    }

    // ==================== Cliente: buscar ou criar ====================

    @Nested
    @DisplayName("Criação/Busca de Cliente")
    class ClienteTests {

        @Test
        @DisplayName("Deve criar novo cliente se telefone não existir no banco")
        void deveCriarNovoClienteSeNaoExistir() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();

            pedidoBotService.criarPedidoViaBot(request);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            Cliente salvo = captor.getValue();
            assertEquals("5581999990000", salvo.getTelefone());
            assertEquals("Carlos", salvo.getNome());
        }

        @Test
        @DisplayName("Deve atualizar cliente existente se telefone já existir")
        void deveAtualizarClienteExistente() {
            Cliente existente = new Cliente();
            existente.setId(5L);
            existente.setTelefone("5581999990000");
            existente.setNome("Nome Antigo");

            when(clienteRepository.findByTelefone("5581999990000")).thenReturn(Optional.of(existente));
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
                Pedido p = inv.getArgument(0);
                p.setId(100L);
                return p;
            });

            PedidoBotRequest request = criarRequestNormal();
            pedidoBotService.criarPedidoViaBot(request);

            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
            verify(clienteRepository).save(captor.capture());
            Cliente salvo = captor.getValue();
            assertEquals(5L, salvo.getId());
            assertEquals("Carlos", salvo.getNome());
        }

        @Test
        @DisplayName("Deve normalizar telefone sem prefixo 55 ao salvar cliente")
        void deveNormalizarTelefoneSemPrefixo() {
            configurarMocksBasicos();
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone("81999990000");

            pedidoBotService.criarPedidoViaBot(request);

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
        @DisplayName("Deve lançar exceção se telefone for nulo")
        void deveLancarExcecaoTelefoneNulo() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Deve lançar exceção se telefone for vazio")
        void deveLancarExcecaoTelefoneVazio() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTelefone("  ");

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Deve lançar exceção se nome for nulo")
        void deveLancarExcecaoNomeNulo() {
            PedidoBotRequest request = criarRequestNormal();
            request.setNome(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Deve lançar exceção se enderecoEntrega for vazio")
        void deveLancarExcecaoEnderecoVazio() {
            PedidoBotRequest request = criarRequestNormal();
            request.setEnderecoEntrega("");

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Deve lançar exceção se formaPagamento for nula")
        void deveLancarExcecaoFormaPagamentoNula() {
            PedidoBotRequest request = criarRequestNormal();
            request.setFormaPagamento(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }

        @Test
        @DisplayName("Deve lançar exceção se tipoPedido for nulo")
        void deveLancarExcecaoTipoPedidoNulo() {
            PedidoBotRequest request = criarRequestNormal();
            request.setTipoPedido(null);

            assertThrows(IllegalArgumentException.class,
                    () -> pedidoBotService.criarPedidoViaBot(request));
        }
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

            when(pedidoRepository.findByChaveIdempotencia("abc-123"))
                    .thenReturn(Optional.of(existente));

            PedidoBotRequest request = criarRequestNormal();
            request.setIdempotencyKey("abc-123");

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertSame(existente, resultado);
            verify(pedidoRepository, never()).save(any(Pedido.class));
            verify(clienteRepository, never()).save(any(Cliente.class));
        }

        @Test
        @DisplayName("Chave nova é gravada no pedido criado")
        void chaveNovaDeveSerGravada() {
            configurarMocksBasicos();
            when(pedidoRepository.findByChaveIdempotencia("nova-chave"))
                    .thenReturn(Optional.empty());

            PedidoBotRequest request = criarRequestNormal();
            request.setIdempotencyKey("nova-chave");

            Pedido resultado = pedidoBotService.criarPedidoViaBot(request);

            assertEquals("nova-chave", resultado.getChaveIdempotencia());
        }

        @Test
        @DisplayName("Sem chave informada, o fluxo normal continua (chave nula)")
        void semChaveDeveCriarNormalmente() {
            configurarMocksBasicos();

            Pedido resultado = pedidoBotService.criarPedidoViaBot(criarRequestNormal());

            assertNull(resultado.getChaveIdempotencia());
            verify(pedidoRepository, never()).findByChaveIdempotencia(any());
        }
    }
}
