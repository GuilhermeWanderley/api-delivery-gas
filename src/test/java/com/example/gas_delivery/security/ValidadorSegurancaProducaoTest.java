package com.example.gas_delivery.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que a aplicação se recusa a subir em produção com configuração insegura.
 */
class ValidadorSegurancaProducaoTest {

    private static final String SENHA_FORTE = "uma-senha-bem-longa-123";
    private static final String CHAVE_FORTE = "0123456789abcdef0123456789abcdef";
    private static final String DOMINIO_REAL = "https://painel.binhodogas.com.br";

    private ValidadorSegurancaProducao validador(
            String ambiente, String senha, String chave, String origens) {
        return new ValidadorSegurancaProducao(ambiente, senha, chave, origens);
    }

    @Test
    @DisplayName("Produção com senha curta é bloqueada")
    void producaoComSenhaPadraoDeveFalhar() {
        var v = validador("producao", "curta123", CHAVE_FORTE, DOMINIO_REAL);
        IllegalStateException erro = assertThrows(IllegalStateException.class, v::validar);
        assertTrue(erro.getMessage().contains("menos de"));
    }

    @Test
    @DisplayName("Produção com senha curta é bloqueada")
    void producaoComSenhaCurtaDeveFalhar() {
        var v = validador("producao", "curta", CHAVE_FORTE, DOMINIO_REAL);
        assertThrows(IllegalStateException.class, v::validar);
    }

    @Test
    @DisplayName("Produção sem BOT_API_KEY é bloqueada (endpoint do robô aberto)")
    void producaoSemChaveDoRoboDeveFalhar() {
        var v = validador("producao", SENHA_FORTE, "", DOMINIO_REAL);
        IllegalStateException erro = assertThrows(IllegalStateException.class, v::validar);
        assertTrue(erro.getMessage().contains("BOT_API_KEY"));
    }

    @Test
    @DisplayName("Produção com CORS só de localhost é bloqueada")
    void producaoComCorsLocalhostDeveFalhar() {
        var v = validador("producao", SENHA_FORTE, CHAVE_FORTE, "http://localhost:5173");
        IllegalStateException erro = assertThrows(IllegalStateException.class, v::validar);
        assertTrue(erro.getMessage().contains("APP_CORS_ORIGINS"));
    }

    @Test
    @DisplayName("Produção bem configurada sobe normalmente")
    void producaoBemConfiguradaDevePassar() {
        var v = validador("producao", SENHA_FORTE, CHAVE_FORTE, DOMINIO_REAL);
        assertDoesNotThrow(v::validar);
    }

    @Test
    @DisplayName("Em desenvolvimento nada é bloqueado (só avisos)")
    void desenvolvimentoNaoDeveBloquear() {
        var v = validador("desenvolvimento", "qualquer-senha", "", "http://localhost:5173");
        assertDoesNotThrow(v::validar);
    }
}
