-- V4: vincula cada pedido ao turno de caixa (kanban só mostra o caixa ABERTO).
-- Sem backfill de propósito: pedidos antigos ficam com caixa_id NULL e,
-- portanto, não aparecem no turno atual — apenas no histórico do turno em que
-- foram criados. O vínculo é por FK, nunca por janela de data/hora (os pedidos
-- antigos estão em UTC e a caixa em BRT).
-- Compatível com ddl-auto=validate do perfil docker.

ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS caixa_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_pedido_caixa ON pedidos (caixa_id);
