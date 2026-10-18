package com.example.gas_delivery.controller;

import com.example.gas_delivery.dto.PedidoBotRequest;
import com.example.gas_delivery.entities.FormaPagamento;
import com.example.gas_delivery.entities.TipoPedido;
import com.example.gas_delivery.service.PedidoBotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trava o contrato de integração com o Typebot: o JSON que o webhook envia
 * deve desserializar corretamente, inclusive com os valores "amigáveis" dos
 * botões e campos vazios.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PedidoBotController.class)
class PedidoBotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoBotService pedidoBotService;

    @Test
    void deveAceitarValoresAmigaveisDoTypebot() throws Exception {
        String body = """
                {
                  "nome": "Maria",
                  "telefone": "81999998888",
                  "cpf": "12345678901",
                  "enderecoEntrega": "Rua B, 456",
                  "tipoPedido": "Gás do povo",
                  "formaPagamento": "Cartão de credito",
                  "precisaTrocoPara": "0"
                }
                """;

        when(pedidoBotService.criarPedidoViaBot(any())).thenReturn(null);

        mockMvc.perform(post("/api/pedidos/via-bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<PedidoBotRequest> captor = ArgumentCaptor.forClass(PedidoBotRequest.class);
        verify(pedidoBotService).criarPedidoViaBot(captor.capture());

        PedidoBotRequest req = captor.getValue();
        assertThat(req.getTipoPedido()).isEqualTo(TipoPedido.GAS_DO_POVO);
        assertThat(req.getFormaPagamento()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
        assertThat(req.getPrecisaTrocoPara()).isEqualByComparingTo("0");
    }

    @Test
    void deveAceitarCampoVazioComoNull() throws Exception {
        String body = """
                {
                  "nome": "Carlos",
                  "telefone": "81999998888",
                  "enderecoEntrega": "Rua A, 123",
                  "tipoPedido": "NORMAL",
                  "formaPagamento": "PIX",
                  "precisaTrocoPara": ""
                }
                """;

        when(pedidoBotService.criarPedidoViaBot(any())).thenReturn(null);

        mockMvc.perform(post("/api/pedidos/via-bot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<PedidoBotRequest> captor = ArgumentCaptor.forClass(PedidoBotRequest.class);
        verify(pedidoBotService).criarPedidoViaBot(captor.capture());

        assertThat(captor.getValue().getPrecisaTrocoPara()).isNull();
        assertThat(captor.getValue().getTipoPedido()).isEqualTo(TipoPedido.NORMAL);
        assertThat(captor.getValue().getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
    }
}
