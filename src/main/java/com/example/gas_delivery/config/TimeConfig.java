package com.example.gas_delivery.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Fixa o fuso horário da aplicação em <b>America/Bahia</b>.
 *
 * <p>Sem isso, o container roda em UTC: uma abertura de caixa às 07h seria gravada
 * como 10h, e a "promoção de quarta-feira" viraria quinta a partir das 21h.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Bahia");

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);

    @PostConstruct
    void configurarFusoPadrao() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA));
        log.info("[CONFIG] fuso horário padrão: {}", ZONA);
    }
}
