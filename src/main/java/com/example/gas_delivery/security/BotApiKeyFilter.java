package com.example.gas_delivery.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protege o endpoint que o Typebot chama para criar pedidos
 * ({@code POST /api/pedidos/via-bot}) com uma chave compartilhada enviada no
 * header {@code X-Api-Key}.
 *
 * <p>Se {@code app.seguranca.bot-api-key} estiver vazia, o endpoint fica
 * <b>aberto</b> (apenas para desenvolvimento local) e um aviso é registrado no
 * startup. Em produção a chave é obrigatória.
 */
@Component
public class BotApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BotApiKeyFilter.class);

    private static final String CAMINHO_BOT = "/api/pedidos/via-bot";
    private static final String HEADER = "X-Api-Key";

    private final String chaveEsperada;
    private final boolean chaveConfigurada;

    public BotApiKeyFilter(@Value("${app.seguranca.bot-api-key:}") String chaveEsperada) {
        this.chaveEsperada = chaveEsperada == null ? "" : chaveEsperada.trim();
        this.chaveConfigurada = !this.chaveEsperada.isEmpty();
        if (!this.chaveConfigurada) {
            log.warn("[SEGURANÇA] app.seguranca.bot-api-key não configurada: o endpoint do robô "
                    + "está ABERTO. Defina BOT_API_KEY antes de publicar em produção.");
        } else {
            log.info("[SEGURANÇA] endpoint do robô protegido por X-Api-Key.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CAMINHO_BOT.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!chaveConfigurada) {
            // Desenvolvimento local: sem chave configurada, não bloqueia.
            filterChain.doFilter(request, response);
            return;
        }

        String recebida = request.getHeader(HEADER);
        if (chaveEsperada.equals(recebida)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("[SEGURANÇA] requisição ao endpoint do robô rejeitada (chave ausente ou inválida). ip={}",
                request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Não autorizado\","
                        + "\"message\":\"Chave do robô ausente ou inválida.\"}");
    }
}
