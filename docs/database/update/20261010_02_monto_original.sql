-- =============================================================================
-- 2026-10-10 · 02 · Monto original de un movimiento convertido (FA-51)
--
-- Un movimiento se registra en la moneda de su cuenta. Si llega en otra, la app
-- lo convierte con las tasas internas, lo deja pendiente y guarda aquí lo que
-- recibió: original_amount y original_currency_code.
--
-- Aditivo: dos columnas nulables y un CHECK. No toca filas.
--
-- En producción se aplica ANTES de desplegar la app de FA-51, que ya escribe y
-- lee las dos columnas. La app anterior las ignora.
--
-- En local la base de FA-51 se reconstruyó desde cero (drop + schema.sql +
-- seed.sql + cargar-datos-local.sql); aplicar este script sobre una base con
-- datos también sirve, porque no los cambia.
--
--   psql -U <usuario> -d financeapp -f 20261010_02_monto_original.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.transactions
    ADD COLUMN original_amount        NUMERIC(18,4),
    ADD COLUMN original_currency_code CHAR(3) REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_transactions_original CHECK (
        (original_amount IS NULL AND original_currency_code IS NULL)
        OR
        (original_amount > 0 AND original_currency_code IS NOT NULL)
    );

COMMENT ON COLUMN finance.transactions.amount IS 'Monto siempre positivo, en la moneda de la cuenta origen (currency_code). El signo lo determina el tipo. Si llegó en otra moneda, es el convertido con la tasa interna (FA-51).';
COMMENT ON COLUMN finance.transactions.destination_amount IS 'Solo en TRANSFER entre cuentas de distinta moneda: lo que efectivamente entra al destino, en la moneda del destino. NULL significa el mismo monto.';
COMMENT ON COLUMN finance.transactions.original_amount IS 'Monto recibido cuando llegó en una moneda distinta a la de la cuenta y se convirtió a amount (FA-51). No cambia aunque se ajuste amount antes de aprobar.';
COMMENT ON COLUMN finance.transactions.original_currency_code IS 'Moneda en que llegó original_amount. NULL si el movimiento llegó en la moneda de su cuenta.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Pierde el registro de lo recibido en los movimientos convertidos;
-- su amount convertido se queda. Desplegar antes la app anterior: la de FA-51
-- escribe las columnas.
-- -----------------------------------------------------------------------------
-- BEGIN;
-- ALTER TABLE finance.transactions
--     DROP CONSTRAINT ck_transactions_original,
--     DROP COLUMN original_currency_code,
--     DROP COLUMN original_amount;
-- COMMENT ON COLUMN finance.transactions.amount IS 'Monto siempre positivo, en la moneda de la cuenta origen. El signo lo determina el tipo.';
-- COMMENT ON COLUMN finance.transactions.destination_amount IS 'Solo en TRANSFER entre cuentas de distinta moneda: lo que efectivamente entra al destino. NULL significa el mismo monto.';
-- COMMIT;
