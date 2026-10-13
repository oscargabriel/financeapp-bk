-- =============================================================================
-- 2026-10-12 · 01 · Refresh tokens y versión de sesión (FIN-47)
--
-- users.token_version cuenta los cambios de contraseña. El access lleva la
-- versión con la que se emitió y cada request Bearer la compara con esta: subirla
-- invalida en el acto todo access y todo refresh emitido antes. La sube el mismo
-- UPDATE que guarda la contraseña nueva.
--
-- refresh_tokens guarda un refresh por fila, agrupados por sesión (family_id).
-- Cada uno sirve una vez; nunca se guarda el valor, solo su SHA-256.
--
-- En producción se aplica ANTES de desplegar la app de FIN-47: la app nueva lee
-- token_version en cada request. La app vieja con la base nueva sigue igual: no
-- nombra la columna ni la tabla. Al desplegar, los access abiertos dan 401 una
-- vez, porque no traen la versión.
--
-- ADD COLUMN con DEFAULT no reescribe filas ni dispara triggers. En local,
-- después de aplicarlo, recargar los datos (test-data.sql siembra refresh):
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261012_01_refresh_tokens.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN finance.users.token_version IS 'Versión de sesión (FIN-47). Sube con cada cambio de contraseña; un access o un refresh con otra versión ya no sirve.';

CREATE TABLE finance.refresh_tokens (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    family_id      UUID         NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    token_version  INTEGER      NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

COMMENT ON TABLE  finance.refresh_tokens IS 'Refresh tokens emitidos (FIN-47). El login abre una sesión (family_id) y cada refresco usa una fila y crea la siguiente, con el mismo expires_at. Reusar una fila ya usada revoca toda la sesión.';
COMMENT ON COLUMN finance.refresh_tokens.token_hash IS 'SHA-256 en hexadecimal del refresh que recibió el cliente. Jamás el valor en claro.';
COMMENT ON COLUMN finance.refresh_tokens.token_version IS 'users.token_version al abrir la sesión. Si ya no coincide, la contraseña cambió y el refresh no sirve.';
COMMENT ON COLUMN finance.refresh_tokens.expires_at IS 'Fin de la sesión: el login más la duración del refresh. Los refrescos lo copian, no lo extienden.';

CREATE INDEX ix_refresh_tokens_family ON finance.refresh_tokens (family_id);

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FIN-47 lee la columna y la tabla.
-- Las sesiones abiertas se pierden.
--
-- BEGIN;
-- DROP TABLE finance.refresh_tokens;
-- ALTER TABLE finance.users DROP COLUMN token_version;
-- COMMIT;
-- -----------------------------------------------------------------------------
