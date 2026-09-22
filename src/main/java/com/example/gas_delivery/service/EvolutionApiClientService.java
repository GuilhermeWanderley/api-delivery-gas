package com.example.gas_delivery.service;

import com.example.gas_delivery.entities.Pedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EvolutionApiClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String instance;
    private final String apiKey;
    private final String pixKey;

    public EvolutionApiClientService(
            RestTemplate restTemplate,
            @Value("${evolution.api.base-url:http://localhost:8080}") String baseUrl,
            @Value("${evolution.api.instance:default}") String instance,
            @Value("${evolution.api.key:}") String apiKey,
            @Value("${app.pix.key:CHAVE_PIX_AQUI}") String pixKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.instance = instance;
        this.apiKey = apiKey;
        this.pixKey = pixKey;
    }

    public void enviarFichaEntregador(Pedido pedido) {
        if (pedido.getEntregador() == null) {
            throw new IllegalStateException("Pedido sem entregador para envio da ficha.");
        }

        String pagamento = pedido.getFormaPagamento().name();
        String trocoInfo = pedido.getPrecisaTrocoPara() == null
                ? ""
                : " (Troco para R$ " + pedido.getPrecisaTrocoPara() + ")";

        String mensagem = "Numero do pedido: " + pedido.getId() + "\n"
                + "Nome: " + pedido.getCliente().getNome() + "\n"
                + "Telefone: " + pedido.getCliente().getTelefone() + "\n"
                + "Forma de pagamento: " + pagamento + trocoInfo + "\n"
                + "Endereco: " + pedido.getEnderecoEntrega();

        enviarMensagem(pedido.getEntregador().getTelefone(), mensagem);
    }

    public void enviarChavePix(String telefone) {
        String mensagem = "Pagamento via PIX\n"
                + "Chave PIX: " + pixKey + "\n"
                + "Por favor, envie o comprovante por aqui.";
        enviarMensagem(telefone, mensagem);
    }

    public void dispararPromocao(String telefone, String mensagem) {
        enviarMensagem(telefone, mensagem);
    }

    private void enviarMensagem(String telefone, String mensagem) {
        String url = baseUrl + "/message/sendText/" + instance;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("number", telefone);
        payload.put("text", mensagem);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("apikey", apiKey);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(url, request, String.class);
    }
}
