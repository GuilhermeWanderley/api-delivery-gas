package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Pedido;
import com.example.gas_delivery.entities.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WhatsappNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappNotificationService.class);

    private static final String PREFIXO_BRASIL = "55";

    private static final Map<Status, String> MENSAGENS_POR_STATUS = Map.of(
            Status.PENDENTE,
            "✅ CPF validado com sucesso! Seu pedido foi aprovado e já está na fila de separação.",

            Status.DESPACHADO,
            "🏍️ O entregador acabou de sair com o seu gás! Fique atento ao portão.",

            Status.ENTREGUE,
            "🏠 Pedido entregue! O *Binho do Gás* agradece a preferência.",

            Status.CANCELADO,
            "❌ Infelizmente seu pedido não pôde ser aprovado para a promoção. Deseja pedir com o valor normal?",

            Status.CPF_REPROVADO,
            "❌ Infelizmente seu pedido não pôde ser aprovado para a promoção. Deseja pedir com o valor normal?"
    );

    private final RestTemplate restTemplate;
    private final String evolutionApiUrl;
    private final String evolutionApiKey;

    /** Quantas vezes tentamos reenviar antes de desistir. */
    @Value("${evolution.api.max-tentativas:3}")
    private int maxTentativas = 3;

    /** Espera base entre tentativas (cresce exponencialmente, com jitter). */
    @Value("${evolution.api.espera-base-ms:700}")
    private long esperaBaseMs = 700;

    public WhatsappNotificationService(
            RestTemplate restTemplate,
            @Value("${evolution.api.url:http://localhost:8090/message/sendText/botgas}") String evolutionApiUrl,
            @Value("${evolution.api.key:}") String evolutionApiKey
    ) {
        this.restTemplate = restTemplate;
        this.evolutionApiUrl = evolutionApiUrl;
        this.evolutionApiKey = evolutionApiKey;
    }

    /**
     * Envia uma notificação assíncrona via WhatsApp ao cliente
     * informando a mudança de status do pedido.
     *
     * @param telefoneCliente telefone do cliente (ex: "81999998888" ou "5581999998888")
     * @param novoStatus      o novo status do pedido
     */
    @Async("whatsappExecutor")
    public void notificarMudancaDeStatus(String telefoneCliente, Status novoStatus) {
        String mensagem = obterMensagemPorStatus(novoStatus);

        if (mensagem == null) {
            log.debug("Nenhuma mensagem mapeada para o status: {}. Notificação não enviada.", novoStatus);
            return;
        }

        String numero = formatarTelefone(telefoneCliente);
        enviarMensagem(numero, mensagem);
    }

    /**
     * Envia ao cliente a chave PIX do pedido e pede o comprovante,
     * reutilizando o mesmo mecanismo de envio das notificações de status.
     *
     * @param pedido   pedido que está sendo pago
     * @param chavePix chave PIX cadastrada na configuração
     */
    public void enviarChavePix(Pedido pedido, String chavePix) {
        String mensagem = "💳 *Pagamento do pedido #" + pedido.getId() + "*\n\n"
                + "Chave PIX: *" + chavePix + "*\n\n"
                + "Depois de pagar, envie o comprovante por aqui. 🧾";
        String numero = formatarTelefone(pedido.getCliente().getTelefone());
        enviarMensagem(numero, mensagem);
    }

    /**
     * Retorna a mensagem de texto correspondente ao status informado,
     * ou {@code null} se o status não possuir mensagem mapeada.
     */
    String obterMensagemPorStatus(Status status) {
        return MENSAGENS_POR_STATUS.get(status);
    }

    /**
     * Garante que o telefone possua o prefixo "55" do Brasil.
     * <p>Exemplo: "81999998888" → "5581999998888"</p>
     */
    String formatarTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String limpo = telefone.replaceAll("[^0-9]", "");
        if (!limpo.startsWith(PREFIXO_BRASIL)) {
            limpo = PREFIXO_BRASIL + limpo;
        }
        return limpo;
    }

    /**
     * Monta o corpo do envio de texto no formato da Evolution API v2
     * (campo "text", não "textMessage").
     */
    Map<String, Object> montarCorpoMensagem(String numero, String texto) {
        return Map.of(
                "number", numero,
                "text", texto,
                "delay", 1500
        );
    }

    /**
     * Envia a mensagem com <b>retry e backoff exponencial</b>.
     *
     * <p>Erros de rede e 5xx são repetidos; respostas 4xx (payload inválido,
     * chave errada) falham na hora, porque repetir não resolveria. O 429
     * (rate limit) é a exceção: vale esperar e tentar de novo.
     */
    private void enviarMensagem(String numero, String texto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", evolutionApiKey);

        Map<String, Object> body = montarCorpoMensagem(numero, texto);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        int tentativas = Math.max(1, maxTentativas);

        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            try {
                restTemplate.postForEntity(evolutionApiUrl, request, String.class);
                log.info("Notificação WhatsApp enviada para {} (tentativa {}/{}).",
                        numero, tentativa, tentativas);
                return;

            } catch (HttpClientErrorException ex) {
                int status = ex.getStatusCode().value();
                if (status == 429 && tentativa < tentativas) {
                    long espera = esperaDaTentativa(tentativa);
                    log.warn("WhatsApp respondeu 429 (rate limit) para {}. Tentativa {}/{}, "
                            + "aguardando {} ms.", numero, tentativa, tentativas, espera);
                    dormir(espera);
                    continue;
                }
                log.error("Falha NÃO repetível ao notificar {} (HTTP {}): {}. "
                        + "Confira a chave da Evolution API e o payload.",
                        numero, status, ex.getMessage());
                return;

            } catch (Exception ex) {
                if (tentativa >= tentativas) {
                    log.error("Falha ao enviar notificação WhatsApp para {} após {} tentativas: {}. "
                            + "O cliente NÃO foi avisado.", numero, tentativas, ex.getMessage(), ex);
                    return;
                }
                long espera = esperaDaTentativa(tentativa);
                log.warn("Tentativa {}/{} de notificar {} falhou ({}). Reenviando em {} ms.",
                        tentativa, tentativas, numero, ex.getMessage(), espera);
                dormir(espera);
            }
        }
    }

    /** Backoff exponencial (700ms, 1400ms, 2800ms...) com jitter para evitar rajada. */
    private long esperaDaTentativa(int tentativa) {
        long exponencial = esperaBaseMs * (1L << (tentativa - 1));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, esperaBaseMs / 2));
        return exponencial + jitter;
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
