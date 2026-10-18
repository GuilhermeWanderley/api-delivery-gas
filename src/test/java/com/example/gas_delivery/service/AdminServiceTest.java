package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Cliente;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private EntregadorRepository entregadorRepository;

    @Mock
    private WhatsappNotificationService whatsappNotificationService;

    @InjectMocks
    private AdminService adminService;

    private Pedido pedidoPendente;
    private Pedido pedidoAguardandoCpf;
    private Entregador entregadorAtivo;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNome("João");
        cliente.setTelefone("5581999999999");
        cliente.setDataCadastro(LocalDateTime.now());

        entregadorAtivo = new Entregador();
        entregadorAtivo.setId(1L);
        entregadorAtivo.setNome("Motoboy");
        entregadorAtivo.setTelefone("5581888888888");
        entregadorAtivo.setAtivo(true);
        entregadorAtivo.setCaixaAtual(BigDecimal.ZERO);

        pedidoPendente = criarPedido(1L, Status.PENDENTE, TipoPedido.NORMAL,
                FormaPagamento.DINHEIRO, "Rua A, 123", new BigDecimal("120.00"));

        pedidoAguardandoCpf = criarPedido(2L, Status.AGUARDANDO_VALIDACAO_CPF, TipoPedido.GAS_DO_POVO,
                FormaPagamento.PIX, "Rua B, 456", new BigDecimal("20.00"));
    }

    private Pedido criarPedido(Long id, Status status, TipoPedido tipo,
                               FormaPagamento pagamento, String endereco, BigDecimal valor) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCliente(cliente);
        p.setStatus(status);
        p.setTipoPedido(tipo);
        p.setFormaPagamento(pagamento);
        p.setEnderecoEntrega(endereco);
        p.setValorTotal(valor);
        p.setDataCriacao(LocalDateTime.now());
        p.setDataAtualizacao(LocalDateTime.now());
        return p;
    }

    // ==================== Testes de Despacho ====================

    @Nested
    @DisplayName("despacharPedido - Regras de validação")
    class DespacharPedidoTests {

        @Test
        @DisplayName("Deve lançar exceção se o enderecoEntrega estiver vazio")
        void deveLancarExcecaoQuandoEnderecoVazio() {
            pedidoPendente.setEnderecoEntrega("");
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));

            assertTrue(ex.getMessage().contains("endereço"));
            verify(pedidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se o enderecoEntrega for null")
        void deveLancarExcecaoQuandoEnderecoNull() {
            pedidoPendente.setEnderecoEntrega(null);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));

            assertTrue(ex.getMessage().contains("endereço"));
        }

        @Test
        @DisplayName("Deve lançar exceção se a formaPagamento for nula")
        void deveLancarExcecaoQuandoFormaPagamentoNula() {
            pedidoPendente.setFormaPagamento(null);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));

            assertTrue(ex.getMessage().contains("forma de pagamento"));
        }

        @Test
        @DisplayName("Deve despachar com sucesso quando status for PENDENTE")
        void deveDespacharComStatusPendente() {
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(entregadorRepository.findById(1L)).thenReturn(Optional.of(entregadorAtivo));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

            Pedido resultado = adminService.despacharPedido(1L, 1L);

            assertEquals(Status.DESPACHADO, resultado.getStatus());
            assertEquals(entregadorAtivo, resultado.getEntregador());
            verify(pedidoRepository).save(any(Pedido.class));
        }

        @Test
        @DisplayName("Deve rejeitar despacho quando status for AGUARDANDO_VALIDACAO_CPF (exige aprovação prévia do CPF)")
        void deveRejeitarDespachoComStatusAguardandoValidacaoCpf() {
            when(pedidoRepository.findById(2L)).thenReturn(Optional.of(pedidoAguardandoCpf));

            assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(2L, 1L));

            verify(pedidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve rejeitar despacho quando status for ENTREGUE")
        void deveRejeitarDespachoQuandoStatusEntregue() {
            pedidoPendente.setStatus(Status.ENTREGUE);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));
        }

        @Test
        @DisplayName("Deve rejeitar despacho quando status for CANCELADO")
        void deveRejeitarDespachoQuandoStatusCancelado() {
            pedidoPendente.setStatus(Status.CANCELADO);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));
        }

        @Test
        @DisplayName("Deve lançar exceção se pedido não for encontrado")
        void deveLancarExcecaoQuandoPedidoNaoEncontrado() {
            when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.despacharPedido(99L, 1L));
        }

        @Test
        @DisplayName("Deve lançar exceção se entregador não for encontrado")
        void deveLancarExcecaoQuandoEntregadorNaoEncontrado() {
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(entregadorRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.despacharPedido(1L, 99L));
        }

        @Test
        @DisplayName("Deve rejeitar despacho para entregador inativo")
        void deveRejeitarDespachoEntregadorInativo() {
            entregadorAtivo.setAtivo(false);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(entregadorRepository.findById(1L)).thenReturn(Optional.of(entregadorAtivo));

            assertThrows(IllegalStateException.class,
                    () -> adminService.despacharPedido(1L, 1L));
        }
    }

    // ==================== Testes de Fechamento de Caixa ====================

    @Nested
    @DisplayName("atualizarPedidoParaEntregue - Cálculo de Caixa")
    class CaixaTests {

        @Test
        @DisplayName("Deve somar ao caixa do entregador quando pagamento for DINHEIRO")
        void deveSomarCaixaComPagamentoDinheiro() {
            pedidoPendente.setStatus(Status.DESPACHADO);
            pedidoPendente.setFormaPagamento(FormaPagamento.DINHEIRO);
            pedidoPendente.setEntregador(entregadorAtivo);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
            when(entregadorRepository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.atualizarPedidoParaEntregue(1L);

            assertEquals(new BigDecimal("120.00"), entregadorAtivo.getCaixaAtual());
            verify(entregadorRepository).save(entregadorAtivo);
        }

        @Test
        @DisplayName("Deve somar ao caixa do entregador quando pagamento for CARTAO_CREDITO")
        void deveSomarCaixaComPagamentoCredito() {
            pedidoPendente.setStatus(Status.DESPACHADO);
            pedidoPendente.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
            pedidoPendente.setEntregador(entregadorAtivo);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
            when(entregadorRepository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.atualizarPedidoParaEntregue(1L);

            assertEquals(new BigDecimal("120.00"), entregadorAtivo.getCaixaAtual());
            verify(entregadorRepository).save(entregadorAtivo);
        }

        @Test
        @DisplayName("Deve somar ao caixa do entregador quando pagamento for CARTAO_DEBITO")
        void deveSomarCaixaComPagamentoDebito() {
            pedidoPendente.setStatus(Status.DESPACHADO);
            pedidoPendente.setFormaPagamento(FormaPagamento.CARTAO_DEBITO);
            pedidoPendente.setEntregador(entregadorAtivo);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
            when(entregadorRepository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.atualizarPedidoParaEntregue(1L);

            assertEquals(new BigDecimal("120.00"), entregadorAtivo.getCaixaAtual());
            verify(entregadorRepository).save(entregadorAtivo);
        }

        @Test
        @DisplayName("NÃO deve somar ao caixa quando pagamento for PIX")
        void naoDeveSomarCaixaComPagamentoPix() {
            pedidoPendente.setStatus(Status.DESPACHADO);
            pedidoPendente.setFormaPagamento(FormaPagamento.PIX);
            pedidoPendente.setEntregador(entregadorAtivo);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.atualizarPedidoParaEntregue(1L);

            assertEquals(BigDecimal.ZERO, entregadorAtivo.getCaixaAtual());
            verify(entregadorRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve acumular valores no caixa ao entregar múltiplos pedidos")
        void deveAcumularCaixaComMultiplosPedidos() {
            pedidoPendente.setStatus(Status.DESPACHADO);
            pedidoPendente.setFormaPagamento(FormaPagamento.DINHEIRO);
            pedidoPendente.setEntregador(entregadorAtivo);

            Pedido pedido2 = criarPedido(3L, Status.DESPACHADO, TipoPedido.NORMAL,
                    FormaPagamento.CARTAO_DEBITO, "Rua C, 789", new BigDecimal("80.00"));
            pedido2.setEntregador(entregadorAtivo);

            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));
            when(pedidoRepository.findById(3L)).thenReturn(Optional.of(pedido2));
            when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
            when(entregadorRepository.save(any(Entregador.class))).thenAnswer(inv -> inv.getArgument(0));

            adminService.atualizarPedidoParaEntregue(1L);
            adminService.atualizarPedidoParaEntregue(3L);

            assertEquals(new BigDecimal("200.00"), entregadorAtivo.getCaixaAtual());
            verify(entregadorRepository, times(2)).save(entregadorAtivo);
        }

        @Test
        @DisplayName("Deve marcar status como ENTREGUE apenas para pedidos DESPACHADOS")
        void deveMarcarEntregueApenasParaDespachados() {
            pedidoPendente.setStatus(Status.PENDENTE);
            when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoPendente));

            assertThrows(IllegalStateException.class,
                    () -> adminService.atualizarPedidoParaEntregue(1L));
        }
    }

    // ==================== Testes de Caixa do Dia por Entregador ====================

    @Nested
    @DisplayName("calcularCaixaDoDiaPorEntregador - Consulta")
    class CaixaDoDiaTests {

        @Test
        @DisplayName("Deve retornar mapa com valores somados por entregador")
        void deveRetornarCaixaPorEntregador() {
            when(entregadorRepository.findAll()).thenReturn(List.of(entregadorAtivo));
            when(pedidoRepository.somarCaixaDoDiaPorEntregador(eq(1L), anyList(), any(), any()))
                    .thenReturn(new BigDecimal("240.00"));

            var resultado = adminService.calcularCaixaDoDiaPorEntregador();

            assertEquals(1, resultado.size());
            assertEquals(new BigDecimal("240.00"), resultado.get(1L));
        }

        @Test
        @DisplayName("Deve retornar zero quando entregador não tiver pedidos no dia")
        void deveRetornarZeroQuandoSemPedidos() {
            when(entregadorRepository.findAll()).thenReturn(List.of(entregadorAtivo));
            when(pedidoRepository.somarCaixaDoDiaPorEntregador(eq(1L), anyList(), any(), any()))
                    .thenReturn(BigDecimal.ZERO);

            var resultado = adminService.calcularCaixaDoDiaPorEntregador();

            assertEquals(BigDecimal.ZERO, resultado.get(1L));
        }
    }
}
