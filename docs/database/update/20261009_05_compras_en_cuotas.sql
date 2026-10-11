-- =============================================================================
-- 2026-10-09 · 05 · Compras en cuotas (FA-108)
--
-- Tabla finance.installment_purchases con cada compra diferida, y en
-- finance.transactions la compra, el número y el capital de cada cuota, que es
-- un gasto normal. finance.committed_credit suma el capital de las cuotas que
-- todavía no llegan: la app lo descuenta del cupo disponible de la tarjeta.
--
-- No toca filas: los movimientos existentes quedan sin cuota.
--
-- En producción se aplica ANTES de desplegar la app de FA-108: la app nueva
-- lee las columnas de la cuota y la función en todas sus consultas de cuentas y
-- movimientos. La app vieja con la base nueva sigue funcionando: no las conoce.
--
-- En local REINICIA LOS DATOS: modifica finance.transactions y test-data.sql
-- cambia. Después de aplicarlo, correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261009_05_compras_en_cuotas.sql
-- =============================================================================

BEGIN;

CREATE TABLE finance.installment_purchases (
    id                     UUID           PRIMARY KEY,
    user_id                UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    account_id             UUID           NOT NULL REFERENCES finance.accounts (id)   ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    category_id            UUID           NOT NULL REFERENCES finance.categories (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    amount                 NUMERIC(18,4)  NOT NULL,
    currency_code          CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    description            VARCHAR(255)   NOT NULL,
    purchase_date          DATE           NOT NULL,
    installment_count      SMALLINT       NOT NULL,
    monthly_interest_rate  NUMERIC(6,4)   NOT NULL,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_installment_purchases_amount CHECK (amount > 0),
    CONSTRAINT ck_installment_purchases_count  CHECK (installment_count BETWEEN 1 AND 48),
    CONSTRAINT ck_installment_purchases_rate   CHECK (monthly_interest_rate BETWEEN 0 AND 10),
    CONSTRAINT ck_installment_purchases_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

COMMENT ON TABLE  finance.installment_purchases IS 'Compra con tarjeta de crédito diferida a cuotas. Sus cuotas son gastos normales con installment_purchase_id; la fila se conserva al cancelar para que las pasadas sigan diciendo de qué compra son.';
COMMENT ON COLUMN finance.installment_purchases.amount IS 'Total de la compra, sin interés. Es la suma exacta del capital de sus cuotas.';
COMMENT ON COLUMN finance.installment_purchases.monthly_interest_rate IS 'Tasa mensual de la tarjeta al registrar la compra, en porcentaje (0 si no tenía). Cambiar la de la tarjeta después no mueve las cuotas.';

CREATE INDEX ix_installment_purchases_user_active
    ON finance.installment_purchases (user_id) WHERE status = 'ACTIVE';

CREATE TRIGGER trg_installment_purchases_updated_at
    BEFORE UPDATE ON finance.installment_purchases
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();

ALTER TABLE finance.transactions
    ADD COLUMN installment_purchase_id UUID REFERENCES finance.installment_purchases (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    ADD COLUMN installment_number      SMALLINT,
    ADD COLUMN installment_principal   NUMERIC(18,4),
    ADD CONSTRAINT ck_transactions_installment CHECK (
        (installment_purchase_id IS NULL AND installment_number IS NULL AND installment_principal IS NULL)
        OR
        (installment_purchase_id IS NOT NULL AND installment_number >= 1 AND installment_principal > 0)
    );

COMMENT ON COLUMN finance.transactions.installment_purchase_id IS 'Compra en cuotas de la que el movimiento es cuota (FA-108). Editarlo o borrarlo a mano no lo saca de ella.';
COMMENT ON COLUMN finance.transactions.installment_principal IS 'Capital de la cuota, sin interés. Mientras la cuota no llega, finance.committed_credit lo descuenta del cupo de la tarjeta.';

CREATE INDEX ix_transactions_installment
    ON finance.transactions (installment_purchase_id, occurred_at) WHERE installment_purchase_id IS NOT NULL;

CREATE OR REPLACE FUNCTION finance.committed_credit(p_account_id UUID)
RETURNS NUMERIC
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(SUM(t.installment_principal), 0)
      FROM finance.transactions t
     WHERE t.account_id = p_account_id
       AND t.installment_purchase_id IS NOT NULL
       AND t.status = 'CONFIRMED'
       AND t.occurred_at > now();
$$;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Borra todas las compras en cuotas; sus cuotas se quedan como
-- gastos sueltos. Desplegar antes la app anterior: la de FA-108 usa la función.
-- -----------------------------------------------------------------------------
-- BEGIN;
-- DROP FUNCTION finance.committed_credit(UUID);
-- DROP INDEX finance.ix_transactions_installment;
-- ALTER TABLE finance.transactions
--     DROP CONSTRAINT ck_transactions_installment,
--     DROP COLUMN installment_principal,
--     DROP COLUMN installment_number,
--     DROP COLUMN installment_purchase_id;
-- DROP TABLE finance.installment_purchases;
-- COMMIT;
