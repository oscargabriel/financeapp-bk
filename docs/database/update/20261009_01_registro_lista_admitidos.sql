-- =============================================================================
-- 2026-10-09 · 01 · Lista de correos admitidos en el registro (FA-103)
--
-- Con la restricción encendida (registro.admitidos.enabled, true por defecto),
-- POST /api/auth/register solo crea usuarios cuyo correo esté aquí, de forma
-- exacta o por su dominio completo. El login no la consulta.
--
-- En producción se aplica ANTES de desplegar la app de FA-103: la app nueva
-- consulta la tabla en cada alta. La app vieja con la base nueva sigue igual.
-- La tabla nace vacía: en producción el registro queda cerrado para todos hasta
-- insertar los correos invitados (docs/despliegue.md, «Correos admitidos»).
--
-- En local no reinicia los datos: solo crea una tabla. Después de aplicarlo,
-- recargar los datos para que bruno/ y el front sigan registrando:
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261009_01_registro_lista_admitidos.sql
-- =============================================================================

BEGIN;

CREATE TABLE finance.registration_allowlist (
    entry       VARCHAR(255) PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_registration_allowlist_entry CHECK (
        entry = lower(entry)
        AND entry ~ '^[^@[:space:]]*@[^@[:space:]]+\.[a-z]{2,}$')
);

COMMENT ON TABLE  finance.registration_allowlist IS 'Correos que pueden registrarse cuando la restricción del alta está encendida. Se mantiene a mano con SQL. El login no la consulta: quitar un correo no afecta al usuario ya registrado.';
COMMENT ON COLUMN finance.registration_allowlist.entry IS 'Un correo exacto (ana@correo.com) o un dominio completo (@correo.com), en minúsculas. Una fila de dominio admite a CUALQUIER cuenta de ese dominio: no usarla con dominios públicos como @gmail.com.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la de FA-103 da 500 en el alta sin la tabla.
--
-- BEGIN;
-- DROP TABLE finance.registration_allowlist;
-- COMMIT;
-- -----------------------------------------------------------------------------
