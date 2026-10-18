-- =============================================================================
-- V2 — Idempotência das requisições do robô
--
-- O Typebot pode reenviar a mesma requisição (retry, duplo clique, reconexão).
-- A chave + restrição única garantem que o pedido não seja criado duas vezes.
-- =============================================================================

ALTER TABLE public.pedidos
    ADD COLUMN IF NOT EXISTS chave_idempotencia character varying(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pedido_chave_idempotencia
    ON public.pedidos (chave_idempotencia);
