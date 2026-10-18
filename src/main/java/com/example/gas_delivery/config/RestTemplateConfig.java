package com.example.gas_delivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * {@link RestTemplate} usado para falar com a Evolution API (WhatsApp).
 *
 * <p>Sem timeout, uma chamada travada prenderia a thread do executor para
 * sempre — por isso definimos limites curtos de conexão e leitura.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${evolution.api.timeout-conexao-ms:3000}") int timeoutConexaoMs,
            @Value("${evolution.api.timeout-leitura-ms:8000}") int timeoutLeituraMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutConexaoMs);
        factory.setReadTimeout(timeoutLeituraMs);
        return new RestTemplate(factory);
    }
}
