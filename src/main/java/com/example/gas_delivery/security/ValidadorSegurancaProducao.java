package com.example.gas_delivery.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Trava de segurança para produção.
 *
 * <p>Quando {@code APP_AMBIENTE=producao}, a aplicação <b>se recusa a subir</b>
 * se encontrar configuração insegura (senha padrão/fraca, chave do robô ausente
 * ou CORS apontando só para localhost). É proposital: melhor não subir do que
 * subir exposto.
 *
 * <p>Fora de produção (desenvolvimento local) apenas registra avisos.
 */
@Component
public class ValidadorSegurancaProducao {

    private static final Logger log = LoggerFactory.getLogger(ValidadorSegurancaProducao.class);

    /** Senha conhecida publicada no repositório — nunca aceita em produção. */
    static final String SENHA_PADRAO_DEV = "binho-admin";

    private static final int TAMANHO_MINIMO_SENHA = 12;
    private static final int TAMANHO_MINIMO_CHAVE_ROBO = 24;

    private final String ambiente;
    private final String adminSenha;
    private final String botApiKey;
    private final String origensPermitidas;

    public ValidadorSegurancaProducao(
            @Value("${app.seguranca.ambiente:desenvolvimento}") String ambiente,
            @Value("${app.seguranca.admin-senha:}") String adminSenha,
            @Value("${app.seguranca.bot-api-key:}") String botApiKey,
            @Value("${app.seguranca.origens-permitidas:}") String origensPermitidas
    ) {
        this.ambiente = ambiente == null ? "" : ambiente.trim();
        this.adminSenha = adminSenha == null ? "" : adminSenha.trim();
        this.botApiKey = botApiKey == null ? "" : botApiKey.trim();
        this.origensPermitidas = origensPermitidas == null ? "" : origensPermitidas.trim();
    }

    @PostConstruct
    void validar() {
        if (!"producao".equalsIgnoreCase(ambiente)) {
            log.info("[SEGURANÇA] ambiente '{}': validação estrita de produção desativada.",
                    ambiente.isEmpty() ? "desenvolvimento" : ambiente);
            avisarSeInseguro();
            return;
        }

        List<String> problemas = new ArrayList<>();

        if (adminSenha.isEmpty()) {
            problemas.add("- APP_ADMIN_SENHA não definida.");
        } else {
            if (SENHA_PADRAO_DEV.equals(adminSenha)) {
                problemas.add("- APP_ADMIN_SENHA ainda é a SENHA PADRÃO do repositório ('"
                        + SENHA_PADRAO_DEV + "').");
            }
            if (adminSenha.length() < TAMANHO_MINIMO_SENHA) {
                problemas.add("- APP_ADMIN_SENHA tem menos de " + TAMANHO_MINIMO_SENHA
                        + " caracteres.");
            }
        }

        if (botApiKey.isEmpty()) {
            problemas.add("- BOT_API_KEY não definida (o endpoint do robô ficaria aberto).");
        } else if (botApiKey.length() < TAMANHO_MINIMO_CHAVE_ROBO) {
            problemas.add("- BOT_API_KEY tem menos de " + TAMANHO_MINIMO_CHAVE_ROBO
                    + " caracteres.");
        }

        if (origensPermitidas.isEmpty() || origensPermitidas.contains("localhost")
                || origensPermitidas.contains("127.0.0.1")) {
            problemas.add("- APP_CORS_ORIGINS aponta apenas para localhost. Use o domínio real "
                    + "(ex.: https://painel.binhodogas.com.br).");
        }

        if (!problemas.isEmpty()) {
            throw new IllegalStateException(
                    System.lineSeparator()
                            + "====================================================================="
                            + System.lineSeparator()
                            + "  STARTUP BLOQUEADO: CONFIGURAÇÃO INSEGURA PARA PRODUÇÃO"
                            + System.lineSeparator()
                            + "====================================================================="
                            + System.lineSeparator()
                            + String.join(System.lineSeparator(), problemas)
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + "  Ajuste o arquivo .env (veja .env.example) e suba novamente."
                            + System.lineSeparator()
                            + "  Para rodar localmente, use APP_AMBIENTE=desenvolvimento."
                            + System.lineSeparator()
                            + "=====================================================================");
        }

        log.info("[SEGURANÇA] ambiente de produção validado: senha forte, chave do robô definida "
                + "e CORS restrito a domínio real.");
    }

    /** Fora de produção, só avisa — para o desenvolvedor não ser pego de surpresa. */
    private void avisarSeInseguro() {
        if (adminSenha.isEmpty()) {
            log.warn("[SEGURANÇA] APP_ADMIN_SENHA vazia: uma senha aleatória será gerada e "
                    + "impressa no log. Defina a variável para ter uma senha fixa.");
        } else if (SENHA_PADRAO_DEV.equals(adminSenha)) {
            log.warn("[SEGURANÇA] usando a SENHA PADRÃO de desenvolvimento. "
                    + "Defina APP_ADMIN_SENHA antes de publicar em produção.");
        }
        if (botApiKey.isEmpty()) {
            log.warn("[SEGURANÇA] BOT_API_KEY vazia: o endpoint do robô está ABERTO. "
                    + "Defina antes de publicar em produção.");
        }
    }
}
