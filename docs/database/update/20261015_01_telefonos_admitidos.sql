-- =============================================================================
-- 2026-10-15 · 01 · Teléfonos en la lista de admitidos (FIN-60)
--
-- registration_allowlist acepta, además de correos y dominios, teléfonos E.164
-- con el +. Un teléfono solo sirve para vincular un chat de Telegram: el alta
-- no lo tiene en cuenta.
--
-- Solo amplía lo aceptado: NO reinicia los datos de la base local, y en
-- producción se aplica antes o después de desplegar la app de FIN-60. Los
-- teléfonos se insertan a mano después de aplicarlo.
--
--   psql -U <usuario> -d financeapp -f 20261015_01_telefonos_admitidos.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.registration_allowlist
    DROP CONSTRAINT ck_registration_allowlist_entry;

ALTER TABLE finance.registration_allowlist
    ADD CONSTRAINT ck_registration_allowlist_entry CHECK (
        entry = lower(entry)
        AND (entry ~ '^[^@[:space:]]*@[^@[:space:]]+\.[a-z]{2,}$'
             OR entry ~ '^\+[1-9][0-9]{6,14}$'));

COMMENT ON TABLE  finance.registration_allowlist IS 'Correos que pueden registrarse cuando la restricción del alta está encendida, y teléfonos que pueden vincular un chat de Telegram (FIN-60). Se mantiene a mano con SQL. El login no la consulta: quitar un correo no afecta al usuario ya registrado. Quitar un teléfono sí corta el acceso del bot desde el siguiente mensaje.';
COMMENT ON COLUMN finance.registration_allowlist.entry IS 'Un correo exacto (ana@correo.com) o un dominio completo (@correo.com), en minúsculas, o un teléfono E.164 con el + (+573001234567). Un teléfono solo sirve para el bot: el alta no lo tiene en cuenta. Una fila de dominio admite a CUALQUIER cuenta de ese dominio: no usarla con dominios públicos como @gmail.com.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Primero se borran los teléfonos, que el CHECK anterior rechaza.
--
-- BEGIN;
-- DELETE FROM finance.registration_allowlist WHERE entry LIKE '+%';
-- ALTER TABLE finance.registration_allowlist DROP CONSTRAINT ck_registration_allowlist_entry;
-- ALTER TABLE finance.registration_allowlist
--     ADD CONSTRAINT ck_registration_allowlist_entry CHECK (
--         entry = lower(entry)
--         AND entry ~ '^[^@[:space:]]*@[^@[:space:]]+\.[a-z]{2,}$');
-- COMMIT;
-- -----------------------------------------------------------------------------
