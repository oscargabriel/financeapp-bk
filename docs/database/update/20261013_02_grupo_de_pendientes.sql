-- =============================================================================
-- 2026-10-13 · 02 · Grupo de los pendientes (FIN-58)
--
-- transactions.group_id agrupa los movimientos que entraron juntos sin
-- confirmar: se confirman o se descartan juntos. Lo genera la aplicación al
-- registrar y se conserva al confirmar. NULL si el movimiento entró confirmado.
--
-- En producción se aplica ANTES de desplegar la app de FIN-58, que escribe la
-- columna. La app vieja con la base nueva sigue igual: no la nombra. El CHECK
-- que exige grupo a todo pendiente va en 20261013_03, DESPUÉS del despliegue:
-- antes rompería el asistente de la app vieja. El porqué está en el design.md
-- del change fin-58-pendiente-sin-confirmar.
--
-- Modifica transactions: en local, después de aplicar este y el 03, recargar
-- los datos:
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261013_02_grupo_de_pendientes.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.transactions
    ADD COLUMN group_id UUID;

COMMENT ON COLUMN finance.transactions.group_id IS 'Grupo de pendientes con que entró (FIN-58): los movimientos que una misma petición dejó sin confirmar, que se confirman o se descartan juntos. Se conserva al confirmar. NULL si entró confirmado.';

-- Confirmar o descartar un grupo lee todos sus movimientos del usuario.
CREATE INDEX ix_transactions_user_group
    ON finance.transactions (user_id, group_id) WHERE group_id IS NOT NULL;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión, con 20261013_03 ya revertido:
--
-- BEGIN;
-- DROP INDEX finance.ix_transactions_user_group;
-- ALTER TABLE finance.transactions DROP COLUMN group_id;
-- COMMIT;
-- -----------------------------------------------------------------------------
