package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.Entregador;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusCaixa;
import com.example.gas_delivery.repository.CaixaRepository;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regras do turno de caixa — com foco na <b>liquidação dos entregadores</b>
 * no fechamento (o saldo que o motoboy carrega volta para zero e o valor
 * passa a integrar o relatório congelado).
 */
@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private EntregadorRepository entregadorRepository;

    private CaixaService service;

    @BeforeEach
    void setUp() {
        service = new CaixaService(caixaRepository, pedidoRepository, entregadorRepository);
    }

    // ---------------- helpers ----------------

    private Caixa caixaAberto() {
        Caixa caixa = new Caixa();
        caixa.setId(1L);
        caixa.setDataAbertura(LocalDateTime.now().minusHours(6));
        caixa.setStatus(StatusCaixa.ABERTO);
        return caixa;
    }

    private Entregador entregador(Long id, String nome, String saldo) {
        Entregador e = new Entregador();
        e.setId(id);
        e.setNome(nome);
        e.setTelefone("558199999000" + id);
        e.setCaixaAtual(new BigDecimal(saldo));
        return e;
    }

    private Pedido pedidoEntregue(Entregador entregador, FormaPagamento forma, String valor) {
        Pedido p = new Pedido();
        p.setEntregador(entregador);
        p.setFormaPagamento(forma);
        p.setValorTotal(new BigDecimal(valor));
        p.setStatus(Status.ENTREGUE);
        p.setDataAtualizacao(LocalDateTime.now());
        return p;
    }

    private void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                "esperado " + esperado + " mas veio " + atual);
    }

    // ---------------- testes ----------------

    @Test
    @DisplayName("Fechar o caixa zera o saldo do entregador e mantém o valor no relatório")
    void fecharDeveZerarSaldoDoEntregador() {
        Caixa caixa = caixaAberto();
        Entregador joao = entregador(2L, "João Silva", "120.00");

        when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                .thenReturn(Optional.of(caixa));
        when(pedidoRepository.findByStatusAndDataAtualizacaoBetweenOrderByDataAtualizacaoAsc(any(), any(), any()))
                .thenReturn(List.of(pedidoEntregue(joao, FormaPagamento.DINHEIRO, "120.00")));
        when(entregadorRepository.findAll()).thenReturn(List.of(joao));
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(inv -> inv.getArgument(0));

        Caixa fechado = service.fechar(null);

        assertEquals(StatusCaixa.FECHADO, fechado.getStatus());
        assertValor("120.00", fechado.getTotalGeral());
        assertEquals(1, fechado.getResumo().size());
        assertEquals("João Silva", fechado.getResumo().get(0).getEntregadorNome());
        // A regra central: o motoboy não fica mais com o dinheiro "veiculado"
        assertValor("0.00", joao.getCaixaAtual());
        verify(entregadorRepository).save(joao);
    }

    @Test
    @DisplayName("Saldo fora do turno (entrega com caixa fechado) entra no relatório e também é zerado")
    void fecharDeveRegistrarSaldoForaDoTurno() {
        Caixa caixa = caixaAberto();
        Entregador carlos = entregador(3L, "Carlos Souza", "200.00");

        when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                .thenReturn(Optional.of(caixa));
        // O turno registrou apenas R$ 120, mas o motoboy carrega R$ 200
        when(pedidoRepository.findByStatusAndDataAtualizacaoBetweenOrderByDataAtualizacaoAsc(any(), any(), any()))
                .thenReturn(List.of(pedidoEntregue(carlos, FormaPagamento.DINHEIRO, "120.00")));
        when(entregadorRepository.findAll()).thenReturn(List.of(carlos));
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(inv -> inv.getArgument(0));

        Caixa fechado = service.fechar(null);

        // 120 (turno) + 80 (diferença não registrada) = 200
        assertValor("200.00", fechado.getTotalGeral());
        assertValor("0.00", carlos.getCaixaAtual());
    }

    @Test
    @DisplayName("Fechar sem caixa aberto deve lançar exceção")
    void fecharSemCaixaAbertoDeveLancarExcecao() {
        when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.fechar(null));
        verify(caixaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Abrir com um caixa já aberto deve lançar exceção")
    void abrirComCaixaJaAbertoDeveLancarExcecao() {
        when(caixaRepository.findFirstByStatusOrderByDataAberturaDesc(StatusCaixa.ABERTO))
                .thenReturn(Optional.of(caixaAberto()));

        assertThrows(IllegalStateException.class, () -> service.abrir("Operador"));
        verify(caixaRepository, never()).save(any());
    }
}
