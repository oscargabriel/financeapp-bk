-- =============================================================================
-- 2026-10-17 · 01 · El origen WEB_CHAT (FIN-112)
--
-- ck_transactions_origin admite WEB_CHAT, el origen de lo que registra el
-- asistente cuando la petición llega por POST /api/assistant/messages.
--
-- Solo amplía lo aceptado: NO reinicia los datos de la base local. En
-- producción se aplica ANTES de desplegar la app de FIN-112: la app nueva
-- inserta WEB_CHAT y el CHECK anterior lo rechazaría. La demo, que trae filas
-- WEB_CHAT, se recarga DESPUÉS del update y del despliegue.
--
--   psql -U <usuario> -d financeapp -f 20261017_01_origen_chat_web.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.transactions
    DROP CONSTRAINT ck_transactions_origin;

ALTER TABLE finance.transactions
    ADD CONSTRAINT ck_transactions_origin CHECK (origin IN ('WEB', 'TELEGRAM', 'IMPORT', 'EMAIL', 'WEB_CHAT'));

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Primero se borran los movimientos WEB_CHAT, que el CHECK anterior
-- rechaza; si alguno estaba confirmado, el trigger de saldos los descuenta.
--
-- BEGIN;
-- DELETE FROM finance.transactions WHERE origin = 'WEB_CHAT';
-- ALTER TABLE finance.transactions DROP CONSTRAINT ck_transactions_origin;
-- ALTER TABLE finance.transactions
--     ADD CONSTRAINT ck_transactions_origin CHECK (origin IN ('WEB', 'TELEGRAM', 'IMPORT', 'EMAIL'));
-- COMMIT;
-- -----------------------------------------------------------------------------
