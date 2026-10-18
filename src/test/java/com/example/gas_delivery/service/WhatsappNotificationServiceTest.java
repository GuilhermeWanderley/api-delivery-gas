package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappNotificationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WhatsappNotificationService service;

    @Nested
    @DisplayName("formatarTelefone")
    class FormatarTelefoneTests {

        @Test
        @DisplayName("Deve concatenar prefixo 55 quando telefone não começa com 55")
        void deveConcatenarPrefixo() {
            String resultado = service.formatarTelefone("81999998888");
            assertEquals("5581999998888", resultado);
        }

        @Test
        @DisplayName("Não deve duplicar prefixo 55 quando telefone já começa com 55")
        void naoDeveDuplicarPrefixo() {
            String resultado = service.formatarTelefone("5581999998888");
            assertEquals("5581999998888", resultado);
        }

        @Test
        @DisplayName("Deve remover caracteres não numéricos")
        void deveRemoverCaracteresNaoNumericos() {
            String resultado = service.formatarTelefone("(81) 99999-8888");
            assertEquals("5581999998888", resultado);
        }

        @Test
        @DisplayName("Deve retornar null quando telefone for null")
        void deveRetornarNullQuandoTelefoneNull() {
            assertNull(service.formatarTelefone(null));
        }
    }

    @Nested
    @DisplayName("obterMensagemPorStatus")
    class MensagemPorStatusTests {

        @Test
        @DisplayName("Deve retornar mensagem de aprovação para PENDENTE (CPF aprovado)")
        void deveRetornarMensagemPendente() {
            String msg = service.obterMensagemPorStatus(Status.PENDENTE);
            assertNotNull(msg);
            assertTrue(msg.contains("CPF validado com sucesso"));
        }

        @Test
        @DisplayName("Deve retornar mensagem de saída para entrega para DESPACHADO")
        void deveRetornarMensagemDespachado() {
            String msg = service.obterMensagemPorStatus(Status.DESPACHADO);
            assertNotNull(msg);
            assertTrue(msg.contains("entregador"));
        }

        @Test
        @DisplayName("Deve retornar mensagem de entrega para ENTREGUE")
        void deveRetornarMensagemEntregue() {
            String msg = service.obterMensagemPorStatus(Status.ENTREGUE);
            assertNotNull(msg);
            assertTrue(msg.contains("Pedido entregue"));
        }

        @Test
        @DisplayName("Deve retornar mensagem de rejeição para CANCELADO")
        void deveRetornarMensagemCancelado() {
            String msg = service.obterMensagemPorStatus(Status.CANCELADO);
            assertNotNull(msg);
            assertTrue(msg.contains("não pôde ser aprovado"));
        }

        @Test
        @DisplayName("Deve retornar mensagem de rejeição para CPF_REPROVADO")
        void deveRetornarMensagemCpfReprovado() {
            String msg = service.obterMensagemPorStatus(Status.CPF_REPROVADO);
            assertNotNull(msg);
            assertTrue(msg.contains("não pôde ser aprovado"));
        }

        @Test
        @DisplayName("Deve retornar null para status sem mensagem mapeada (EM_PREENCHIMENTO)")
        void deveRetornarNullParaStatusSemMensagem() {
            assertNull(service.obterMensagemPorStatus(Status.EM_PREENCHIMENTO));
        }

        @Test
        @DisplayName("Deve retornar null para AGUARDANDO_VALIDACAO_CPF")
        void deveRetornarNullParaAguardandoValidacao() {
            assertNull(service.obterMensagemPorStatus(Status.AGUARDANDO_VALIDACAO_CPF));
        }
    }

    @Nested
    @DisplayName("notificarMudancaDeStatus")
    class NotificarTests {

        @Test
        @DisplayName("Não deve chamar a API quando o status não tem mensagem mapeada")
        void naoDeveChamarApiParaStatusSemMensagem() {
            service.notificarMudancaDeStatus("81999998888", Status.EM_PREENCHIMENTO);

            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Deve montar payload no formato v2 do sendText (campo 'text')")
        void deveMontarPayloadComCampoText() {
            Map<String, Object> body = service.montarCorpoMensagem("5581999998888", "Olá, cliente!");

            assertEquals("5581999998888", body.get("number"));
            assertEquals("Olá, cliente!", body.get("text"));
            assertEquals(1500, body.get("delay"));
            assertFalse(body.containsKey("textMessage"));
        }
    }
}
