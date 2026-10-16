-- =============================================================================
-- 2026-10-15 · 02 · El origen EMAIL (FIN-101)
--
-- ck_transactions_origin admite EMAIL, reservado para la ingesta desde correo
-- (FIN-12). Ningún endpoint lo produce todavía: solo lo traen los datos de demo
-- y de prueba.
--
-- Solo amplía lo aceptado: NO reinicia los datos de la base local, y en
-- producción se aplica antes o después de desplegar la app de FIN-101. La demo,
-- que trae filas EMAIL, se recarga DESPUÉS del update y del despliegue: la app
-- vieja no sabe leer ese origen y rompería la lista de pendientes de demo@.
--
--   psql -U <usuario> -d financeapp -f 20261015_02_origen_email.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.transactions
    DROP CONSTRAINT ck_transactions_origin;

ALTER TABLE finance.transactions
    ADD CONSTRAINT ck_transactions_origin CHECK (origin IN ('WEB', 'TELEGRAM', 'IMPORT', 'EMAIL'));

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Primero se borran los movimientos EMAIL, que el CHECK anterior
-- rechaza; si alguno estaba confirmado, el trigger de saldos los descuenta.
--
-- BEGIN;
-- DELETE FROM finance.transactions WHERE origin = 'EMAIL';
-- ALTER TABLE finance.transactions DROP CONSTRAINT ck_transactions_origin;
-- ALTER TABLE finance.transactions
--     ADD CONSTRAINT ck_transactions_origin CHECK (origin IN ('WEB', 'TELEGRAM', 'IMPORT'));
-- COMMIT;
-- -----------------------------------------------------------------------------
