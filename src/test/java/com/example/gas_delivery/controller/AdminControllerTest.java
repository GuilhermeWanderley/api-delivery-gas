package com.example.gas_delivery.controller;

import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.repository.EntregadorRepository;
import com.example.gas_delivery.repository.PedidoRepository;
import com.example.gas_delivery.service.AdminService;
import com.example.gas_delivery.service.CaixaService;
import com.example.gas_delivery.service.ConfiguracaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void deveListarPedidos() throws Exception {
        when(pedidoRepository.findAll(any(Sort.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/pedidos"))
                .andExpect(status().isOk());
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
