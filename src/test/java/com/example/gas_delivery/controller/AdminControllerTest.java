package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.Caixa;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import com.example.gas_delivery.entities.StatusPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import com.example.gas_delivery.service.AdminService;
import com.example.gas_delivery.service.CaixaService;
import com.example.gas_delivery.service.ConfiguracaoService;
import com.example.gas_delivery.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre a camada HTTP dos endpoints administrativos (o despacho, o cancelamento,
 * a entrega e a aprovação/reprovação de CPF), garantindo o roteamento e o
 * tratamento de erro centralizado.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoRepository pedidoRepository;

    @MockBean
    private EntregadorRepository entregadorRepository;

    @MockBean
    private AdminService adminService;

    @MockBean
    private CaixaService caixaService;

    @MockBean
    private ConfiguracaoService configuracaoService;

    @MockBean
    private PedidoService pedidoService;

    @Test
    void deveListarPedidos() throws Exception {
        when(pedidoRepository.findAll(any(Sort.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/pedidos"))
                .andExpect(status().isOk());
    }

    @Test
    void deveRetornarKanbanVazioQuandoNaoHaCaixaAberto() throws Exception {
        when(caixaService.caixaAberto()).thenReturn(null);

        mockMvc.perform(get("/api/admin/pedidos").param("somenteTurnoAtual", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(pedidoRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void deveListarSomentePedidosDoCaixaAberto() throws Exception {
        Caixa caixa = new Caixa();
        caixa.setId(3L);
        when(caixaService.caixaAberto()).thenReturn(caixa);
        when(pedidoRepository.findByCaixaIdOrderByIdDesc(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/pedidos").param("somenteTurnoAtual", "true"))
                .andExpect(status().isOk());

        verify(pedidoRepository).findByCaixaIdOrderByIdDesc(3L);
        verify(pedidoRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void deveListarPedidosDoCaixaComFiltroDeStatus() throws Exception {
        Caixa caixa = new Caixa();
        caixa.setId(3L);
        when(caixaService.caixaAberto()).thenReturn(caixa);
        when(pedidoRepository.findByCaixaIdAndStatusOrderByIdDesc(3L, Status.PENDENTE))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/pedidos")
                        .param("somenteTurnoAtual", "true")
                        .param("status", "PENDENTE"))
                .andExpect(status().isOk());

        verify(pedidoRepository).findByCaixaIdAndStatusOrderByIdDesc(3L, Status.PENDENTE);
    }

    @Test
    void deveListarPedidosDoHistoricoDoCaixa() throws Exception {
        when(pedidoRepository.findByCaixaIdOrderByIdDesc(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/caixa/5/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(pedidoRepository).findByCaixaIdOrderByIdDesc(5L);
    }

    @Test
    void deveDespacharPedido() throws Exception {
        when(adminService.despacharPedido(1L, 2L)).thenReturn(new Pedido());

        mockMvc.perform(put("/api/admin/pedidos/1/despachar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entregadorId\": 2}"))
                .andExpect(status().isOk());

        verify(adminService).despacharPedido(1L, 2L);
    }

    @Test
    void deveRejeitarDespachoSemEntregadorId() throws Exception {
        mockMvc.perform(put("/api/admin/pedidos/1/despachar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveCancelarPedido() throws Exception {
        when(adminService.cancelarPedido(1L)).thenReturn(new Pedido());

        mockMvc.perform(put("/api/admin/pedidos/1/cancelar"))
                .andExpect(status().isOk());

        verify(adminService).cancelarPedido(1L);
    }

    @Test
    void deveAtualizarStatusDePagamento() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setStatusPagamento(StatusPagamento.CONFIRMADO);
        when(adminService.atualizarStatusPagamento(1L, StatusPagamento.CONFIRMADO)).thenReturn(pedido);

        mockMvc.perform(put("/api/admin/pedidos/1/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPagamento").value("CONFIRMADO"));

        verify(adminService).atualizarStatusPagamento(1L, StatusPagamento.CONFIRMADO);
    }

    @Test
    void deveRecusarTransicaoDePagamentoInvalida() throws Exception {
        when(adminService.atualizarStatusPagamento(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Transição de pagamento inválida."));

        mockMvc.perform(put("/api/admin/pedidos/1/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ESTORNADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transição de pagamento inválida."));
    }

    @Test
    void deveRecusarPagamentoEmPedidoNaoPix() throws Exception {
        when(adminService.atualizarStatusPagamento(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Este pedido não é PIX."));

        mockMvc.perform(put("/api/admin/pedidos/1/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este pedido não é PIX."));
    }

    @Test
    void deveCriarPedidoManual() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        when(pedidoService.criarPedidoManual(any(PedidoBotRequest.class))).thenReturn(pedido);

        mockMvc.perform(post("/api/admin/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "telefone": "81999998888",
                                  "nome": "Dona Maria",
                                  "tipoPedido": "NORMAL",
                                  "enderecoEntrega": "Rua A, 123",
                                  "formaPagamento": "DINHEIRO",
                                  "precisaTrocoPara": "50"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<PedidoBotRequest> captor = ArgumentCaptor.forClass(PedidoBotRequest.class);
        verify(pedidoService).criarPedidoManual(captor.capture());
        PedidoBotRequest request = captor.getValue();
        assertEquals(TipoPedido.NORMAL, request.getTipoPedido());
        assertEquals(FormaPagamento.DINHEIRO, request.getFormaPagamento());
        assertEquals("Dona Maria", request.getNome());
    }

    @Test
    void deveRecusarPedidoManualSemCaixaAberto() throws Exception {
        when(pedidoService.criarPedidoManual(any(PedidoBotRequest.class)))
                .thenThrow(new IllegalStateException("Abra o caixa antes de lançar um pedido."));

        mockMvc.perform(post("/api/admin/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "telefone": "81999998888",
                                  "nome": "Dona Maria",
                                  "tipoPedido": "NORMAL",
                                  "enderecoEntrega": "Rua A, 123",
                                  "formaPagamento": "DINHEIRO"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Abra o caixa antes de lançar um pedido."));
    }

    @Test
    void deveValidarCamposObrigatoriosDoPedidoManual() throws Exception {
        mockMvc.perform(post("/api/admin/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\": \"81999998888\"}"))
                .andExpect(status().isBadRequest());

        verify(pedidoService, never()).criarPedidoManual(any());
    }

    @Test
    void deveMarcarPedidoEntregue() throws Exception {
        when(adminService.atualizarPedidoParaEntregue(1L)).thenReturn(new Pedido());

        mockMvc.perform(put("/api/admin/pedidos/1/entregue"))
                .andExpect(status().isOk());

        verify(adminService).atualizarPedidoParaEntregue(1L);
    }

    @Test
    void deveAprovarCpf() throws Exception {
        when(adminService.aprovarCpfGasDoPovo(1L)).thenReturn(new Pedido());

        mockMvc.perform(put("/api/admin/pedidos/1/aprovar-cpf"))
                .andExpect(status().isOk());

        verify(adminService).aprovarCpfGasDoPovo(1L);
    }

    @Test
    void deveReprovarCpf() throws Exception {
        when(adminService.reprovarCpfGasDoPovo(1L)).thenReturn(new Pedido());

        mockMvc.perform(put("/api/admin/pedidos/1/reprovar-cpf"))
                .andExpect(status().isOk());

        verify(adminService).reprovarCpfGasDoPovo(1L);
    }
}
