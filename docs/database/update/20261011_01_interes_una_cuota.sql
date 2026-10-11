-- =============================================================================
-- 2026-10-11 · 01 · Interés en las compras a 1 cuota (FA-134)
--
-- Las cuentas CREDIT guardan si cobran interés en las compras a 1 cuota. Es un
-- boolean NOT NULL con DEFAULT FALSE: las tarjetas existentes quedan sin
-- interés a 1 cuota, como hasta ahora. Fuera de una CREDIT tiene que ser FALSE:
-- ck_accounts_credit_fields lo incluye.
--
-- ADD COLUMN con DEFAULT no reescribe filas ni dispara triggers: ningún saldo
-- ni updated_at cambia al aplicarlo. No reinicia los datos locales.
--
-- En producción se aplica ANTES de desplegar la app de FA-134: la app nueva
-- lee y escribe la columna. La app vieja con la base nueva sigue funcionando:
-- no la nombra, y sus INSERT toman el DEFAULT.
--
--   psql -U <usuario> -d financeapp -f 20261011_01_interes_una_cuota.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.accounts
    ADD COLUMN charges_single_installment_interest BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE finance.accounts DROP CONSTRAINT ck_accounts_credit_fields;
ALTER TABLE finance.accounts ADD CONSTRAINT ck_accounts_credit_fields CHECK (
    type = 'CREDIT'
    OR (credit_limit IS NULL AND statement_day IS NULL AND payment_due_day IS NULL
        AND monthly_interest_rate IS NULL AND charges_single_installment_interest = FALSE)
);

COMMENT ON COLUMN finance.accounts.charges_single_installment_interest IS 'Si la tarjeta de crédito cobra interés en las compras a 1 cuota (FA-134). FALSE fuera de una tarjeta. Las compras ya registradas no se recalculan al cambiarlo.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FA-134 lee la columna. Las
-- opciones guardadas se pierden; las compras ya registradas conservan sus cuotas.
--
-- BEGIN;
-- ALTER TABLE finance.accounts DROP CONSTRAINT ck_accounts_credit_fields;
-- ALTER TABLE finance.accounts ADD CONSTRAINT ck_accounts_credit_fields CHECK (
--     type = 'CREDIT'
--     OR (credit_limit IS NULL AND statement_day IS NULL AND payment_due_day IS NULL
--         AND monthly_interest_rate IS NULL)
-- );
-- ALTER TABLE finance.accounts DROP COLUMN charges_single_installment_interest;
-- COMMIT;
-- -----------------------------------------------------------------------------
