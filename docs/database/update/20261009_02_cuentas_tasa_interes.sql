-- =============================================================================
-- 2026-10-09 · 02 · Tasa de interés mensual de las tarjetas de crédito (FA-105)
--
-- Las cuentas CREDIT guardan su tasa de interés mensual en porcentaje (2.15 es
-- el 2,15 % mensual), de 0 a 10. Es opcional, como el cupo, y fuera de una
-- CREDIT va en NULL: ck_accounts_credit_fields la incluye.
--
-- Las filas existentes quedan con la tasa en NULL. ADD COLUMN no dispara
-- triggers: ningún saldo cambia al aplicarlo.
--
-- En producción se aplica ANTES de desplegar la app de FA-105: la app nueva
-- lee y escribe la columna. La app vieja con la base nueva sigue funcionando.
--
-- En local REINICIA LOS DATOS: test-data.sql y demo-data.sql cargan tasas.
-- Después de aplicarlo, correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261009_02_cuentas_tasa_interes.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.accounts
    ADD COLUMN monthly_interest_rate NUMERIC(6,4),
    ADD CONSTRAINT ck_accounts_monthly_interest_rate
        CHECK (monthly_interest_rate IS NULL OR monthly_interest_rate BETWEEN 0 AND 10);

ALTER TABLE finance.accounts DROP CONSTRAINT ck_accounts_credit_fields;
ALTER TABLE finance.accounts ADD CONSTRAINT ck_accounts_credit_fields CHECK (
    type = 'CREDIT'
    OR (credit_limit IS NULL AND statement_day IS NULL AND payment_due_day IS NULL
        AND monthly_interest_rate IS NULL)
);

COMMENT ON COLUMN finance.accounts.monthly_interest_rate IS 'Tasa de interés mensual de una tarjeta de crédito, en porcentaje: 2.15 es el 2,15 % mensual. NULL si no se ha cargado.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FA-105 lee la columna. Las tasas
-- cargadas se pierden.
--
-- BEGIN;
-- ALTER TABLE finance.accounts DROP CONSTRAINT ck_accounts_credit_fields;
-- ALTER TABLE finance.accounts ADD CONSTRAINT ck_accounts_credit_fields CHECK (
--     type = 'CREDIT'
--     OR (credit_limit IS NULL AND statement_day IS NULL AND payment_due_day IS NULL)
-- );
-- ALTER TABLE finance.accounts DROP COLUMN monthly_interest_rate;
-- COMMIT;
-- -----------------------------------------------------------------------------
