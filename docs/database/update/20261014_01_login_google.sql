-- =============================================================================
-- 2026-10-14 · 01 · Login con Google (FIN-52)
--
-- users.google_sub guarda el identificador estable de la cuenta Google (el claim
-- sub del ID token, hasta 255 caracteres). El login con Google busca por él, no
-- por el correo. Único entre los no borrados, como el correo: un usuario borrado
-- libera su cuenta Google.
--
-- password_hash pasa a admitir nulo: un usuario registrado con Google no tiene
-- contraseña. ck_users_metodo exige al menos uno de los dos métodos.
--
-- En producción se aplica ANTES de desplegar la app de FIN-52: la app nueva lee
-- google_sub. La app vieja con la base nueva sigue igual: no nombra la columna,
-- y siempre inserta un hash. El porqué está en el design.md del change
-- fin-52-login-google.
--
-- Modifica users: en local, después de aplicarlo, recargar los datos:
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261014_01_login_google.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE finance.users
    ADD COLUMN google_sub VARCHAR(255);

ALTER TABLE finance.users
    ADD CONSTRAINT ck_users_metodo CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL);

COMMENT ON TABLE  finance.users IS 'Persona que registra finanzas. Entra con email + hash, con su cuenta Google o con los dos (FIN-52); Telegram es un canal adicional que se vincula después.';
COMMENT ON COLUMN finance.users.password_hash IS 'Hash BCrypt/Argon2 calculado en la aplicación. Jamás la contraseña en claro. Nulo si el usuario se registró con Google y no ha definido una.';
COMMENT ON COLUMN finance.users.google_sub IS 'Claim sub del ID token de Google (FIN-52): identificador estable de la cuenta Google vinculada. El login con Google busca por él, no por el correo.';

CREATE UNIQUE INDEX ux_users_google_sub
    ON finance.users (google_sub) WHERE google_sub IS NOT NULL AND deleted_at IS NULL;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FIN-52 lee google_sub. Los
-- usuarios registrados solo con Google pierden la forma de entrar, y el SET NOT
-- NULL falla mientras exista alguno: hay que borrarlos o darles un hash antes.
--
-- BEGIN;
-- DROP INDEX finance.ux_users_google_sub;
-- ALTER TABLE finance.users DROP CONSTRAINT ck_users_metodo;
-- ALTER TABLE finance.users DROP COLUMN google_sub;
-- ALTER TABLE finance.users ALTER COLUMN password_hash SET NOT NULL;
-- COMMENT ON TABLE  finance.users IS 'Persona que registra finanzas. La autenticación es propia (email + hash); Telegram es un canal adicional que se vincula después.';
-- COMMENT ON COLUMN finance.users.password_hash IS 'Hash BCrypt/Argon2 calculado en la aplicación. Jamás la contraseña en claro.';
-- COMMIT;
-- -----------------------------------------------------------------------------
