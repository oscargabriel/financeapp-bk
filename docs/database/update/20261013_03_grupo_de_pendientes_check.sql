-- =============================================================================
-- 2026-10-13 · 03 · Todo pendiente tiene grupo (FIN-58)
--
-- Los pendientes creados antes de 20261013_02, o por la app vieja mientras se
-- desplegaba la nueva, no tienen grupo: cada uno recibe uno propio. Desde ahí
-- ck_transactions_group exige grupo a todo pendiente, sea cual sea su origen.
--
-- En producción se aplica DESPUÉS de desplegar la app de FIN-58: la app vieja
-- crea pendientes sin grupo y este CHECK los rechazaría.
--
-- El UPDATE no cambia monto, moneda ni fecha, así que el trigger de la tasa
-- conserva exchange_rate y amount_base; el de saldos ignora los pendientes.
--
--   psql -U <usuario> -d financeapp -f 20261013_03_grupo_de_pendientes_check.sql
-- =============================================================================

BEGIN;

UPDATE finance.transactions
   SET group_id = gen_random_uuid()
 WHERE status = 'PENDING'
   AND group_id IS NULL;

ALTER TABLE finance.transactions
    ADD CONSTRAINT ck_transactions_group CHECK (status = 'CONFIRMED' OR group_id IS NOT NULL);

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Los grupos que recibieron los pendientes viejos se quedan: sin el
-- CHECK no estorban.
--
-- ALTER TABLE finance.transactions DROP CONSTRAINT ck_transactions_group;
-- -----------------------------------------------------------------------------
