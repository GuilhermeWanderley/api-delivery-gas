package com.example.gas_delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que a blindagem funciona de ponta a ponta:
 * o painel exige login e o endpoint do robô exige a chave compartilhada.
 */
@SpringBootTest(properties = {
        "app.seguranca.admin-usuario=teste",
        "app.seguranca.admin-senha=segredo",
        "app.seguranca.bot-api-key=chave-do-robo"
})
@AutoConfigureMockMvc
class SegurancaIntegrationTest {

    private static final String ENDPOINT_ROBO = "/api/pedidos/via-bot";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Painel: sem credencial retorna 401")
    void painelSemCredencialRetorna401() throws Exception {
        mockMvc.perform(get("/api/admin/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Painel: credencial correta retorna 200")
    void painelComCredencialCorretaRetorna200() throws Exception {
        mockMvc.perform(get("/api/admin/pedidos").with(httpBasic("teste", "segredo")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Painel: senha errada retorna 401")
    void painelComSenhaErradaRetorna401() throws Exception {
        mockMvc.perform(get("/api/admin/pedidos").with(httpBasic("teste", "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Robô: sem a chave X-Api-Key retorna 401")
    void roboSemChaveRetorna401() throws Exception {
        mockMvc.perform(post(ENDPOINT_ROBO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Robô: com a chave correta passa pela segurança (400 por corpo vazio)")
    void roboComChaveCorretaPassaPelaSeguranca() throws Exception {
        mockMvc.perform(post(ENDPOINT_ROBO)
                        .header("X-Api-Key", "chave-do-robo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Health check continua público (usado pelos containers)")
    void healthContinuaPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
