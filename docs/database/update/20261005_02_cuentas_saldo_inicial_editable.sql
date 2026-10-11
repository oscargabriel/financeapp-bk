-- =============================================================================
-- 2026-10-05 · 02 · Saldo inicial editable sin descuadrar el vigente (FA-24)
--
-- PATCH /api/accounts/{id} permite corregir initial_balance. current_balance
-- es el inicial más el efecto de los movimientos, y hasta ahora solo lo movían
-- el alta (trg_accounts_seed_balance) y los movimientos. Este trigger lo corre
-- en la misma diferencia cuando cambia el inicial, con o sin movimientos.
--
-- No toca filas: ninguna cuenta cambia de saldo al aplicarlo.
--
-- En producción se aplica ANTES de desplegar la app de FA-24: con la app nueva
-- y la base vieja, cambiar el saldo inicial dejaría el vigente con el viejo.
--
--   psql -U <usuario> -d financeapp -f 20261005_02_cuentas_saldo_inicial_editable.sql
-- =============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION finance.shift_account_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.current_balance := NEW.current_balance + (NEW.initial_balance - OLD.initial_balance);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_shift_balance
    BEFORE UPDATE OF initial_balance ON finance.accounts
    FOR EACH ROW
    WHEN (NEW.initial_balance IS DISTINCT FROM OLD.initial_balance)
    EXECUTE FUNCTION finance.shift_account_balance();

COMMENT ON COLUMN finance.accounts.current_balance IS 'Saldo vigente: initial_balance más el efecto de los movimientos. Lo mantienen los triggers trg_transactions_sync_balance y trg_accounts_shift_balance; la aplicación NUNCA lo escribe directamente. En una tarjeta de crédito un valor negativo es la deuda, y el cupo disponible es credit_limit + current_balance.';
COMMENT ON COLUMN finance.accounts.initial_balance IS 'Saldo con el que la cuenta entra al sistema. Se copia a current_balance al crearla, y si después cambia, current_balance se corre en la misma diferencia.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: sin el trigger, un PATCH de initialBalance
-- descuadraría el saldo vigente. Los saldos ya corridos se quedan como están,
-- que es lo correcto: siguen siendo el inicial más los movimientos.
--
-- BEGIN;
-- DROP TRIGGER trg_accounts_shift_balance ON finance.accounts;
-- DROP FUNCTION finance.shift_account_balance();
-- COMMENT ON COLUMN finance.accounts.current_balance IS 'Saldo vigente. Lo mantiene el trigger trg_transactions_sync_balance; la aplicación NUNCA lo escribe directamente. En una tarjeta de crédito un valor negativo es la deuda, y el cupo disponible es credit_limit + current_balance.';
-- COMMENT ON COLUMN finance.accounts.initial_balance IS 'Saldo con el que la cuenta entra al sistema. Se copia a current_balance al crearla.';
-- COMMIT;
-- -----------------------------------------------------------------------------
