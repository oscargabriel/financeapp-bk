-- =============================================================================
-- 2026-10-08 · 01 · Estado de aprobación en los movimientos (FA-76)
--
-- Lo que registre el asistente de IA (FA-77) entra PENDING y no tiene efecto
-- hasta que el usuario lo aprueba: no mueve current_balance ni cuenta en el
-- reporte de movimientos, la consulta de saldo ni el gasto mensual.
--
-- Las filas existentes quedan CONFIRMED por el default. ADD COLUMN no dispara
-- triggers: ningún saldo cambia al aplicarlo.
--
-- En producción se aplica ANTES de desplegar la app de FA-76: la app nueva
-- inserta y lee status. La app vieja con la base nueva sigue funcionando.
--
-- En local REINICIA LOS DATOS: después de aplicarlo, correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261008_01_transacciones_estado_aprobacion.sql
-- =============================================================================

BEGIN;

ALTER TABLE finance.transactions
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED',
    ADD CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'CONFIRMED'));

COMMENT ON COLUMN finance.transactions.status IS 'PENDING: registrado por el asistente y sin efecto hasta aprobarse; no mueve current_balance ni cuenta en reportes. CONFIRMED: todo lo demás. Rechazar un pendiente lo borra.';

CREATE INDEX ix_transactions_pending
    ON finance.transactions (user_id, occurred_at DESC) WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION finance.sync_account_balances()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.status = 'CONFIRMED' THEN
        UPDATE finance.accounts
           SET current_balance = current_balance - finance.balance_delta(OLD.type, OLD.amount)
         WHERE id = OLD.account_id;

        IF OLD.type = 'TRANSFER' THEN
            UPDATE finance.accounts
               SET current_balance = current_balance - COALESCE(OLD.destination_amount, OLD.amount)
             WHERE id = OLD.destination_account_id;
        END IF;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.status = 'CONFIRMED' THEN
        UPDATE finance.accounts
           SET current_balance = current_balance + finance.balance_delta(NEW.type, NEW.amount)
         WHERE id = NEW.account_id;

        IF NEW.type = 'TRANSFER' THEN
            UPDATE finance.accounts
               SET current_balance = current_balance + COALESCE(NEW.destination_amount, NEW.amount)
             WHERE id = NEW.destination_account_id;
        END IF;
    END IF;

    RETURN NULL;
END;
$$;

CREATE OR REPLACE VIEW finance.v_monthly_spending AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           SUM(t.amount_base)                                               AS total_spent,
           COUNT(*)                                                         AS transaction_count
      FROM finance.transactions t
      JOIN finance.users u ON u.id = t.user_id
     WHERE t.type = 'EXPENSE'
       AND t.status = 'CONFIRMED'
     GROUP BY t.user_id, 2
),
budget AS (
    SELECT b.user_id,
           b.period_month,
           b.amount AS budget_amount
      FROM finance.budgets b
     WHERE b.category_id IS NULL
)
SELECT COALESCE(s.user_id, b.user_id)               AS user_id,
       COALESCE(s.period_month, b.period_month)     AS period_month,
       u.base_currency_code                         AS currency_code,
       COALESCE(s.total_spent, 0)                   AS total_spent,
       b.budget_amount,
       b.budget_amount - COALESCE(s.total_spent, 0) AS remaining,
       CASE WHEN b.budget_amount IS NOT NULL
            THEN ROUND(COALESCE(s.total_spent, 0) * 100 / b.budget_amount, 2)
       END                                          AS percent_used,
       COALESCE(s.transaction_count, 0)             AS transaction_count
  FROM spent s
  FULL OUTER JOIN budget b
    ON b.user_id = s.user_id
   AND b.period_month = s.period_month
  JOIN finance.users u
    ON u.id = COALESCE(s.user_id, b.user_id);

CREATE OR REPLACE VIEW finance.v_monthly_spending_by_category AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           t.category_id,
           SUM(t.amount_base)                                               AS total_spent,
           COUNT(*)                                                         AS transaction_count
      FROM finance.transactions t
      JOIN finance.users u ON u.id = t.user_id
     WHERE t.type = 'EXPENSE'
       AND t.status = 'CONFIRMED'
     GROUP BY t.user_id, 2, t.category_id
),
budget AS (
    SELECT b.user_id,
           b.period_month,
           b.category_id,
           b.amount AS category_budget
      FROM finance.budgets b
     WHERE b.category_id IS NOT NULL
),
merged AS (
    SELECT COALESCE(s.user_id, b.user_id)           AS user_id,
           COALESCE(s.period_month, b.period_month) AS period_month,
           COALESCE(s.category_id, b.category_id)   AS category_id,
           COALESCE(s.total_spent, 0)               AS total_spent,
           COALESCE(s.transaction_count, 0)         AS transaction_count,
           b.category_budget
      FROM spent s
      FULL OUTER JOIN budget b
        ON b.user_id = s.user_id
       AND b.period_month = s.period_month
       AND b.category_id = s.category_id
)
SELECT m.user_id,
       m.period_month,
       m.category_id,
       c.name                            AS category_name,
       c.icon                            AS category_icon,
       c.color                           AS category_color,
       u.base_currency_code              AS currency_code,
       m.total_spent,
       m.category_budget,
       m.category_budget - m.total_spent AS remaining,
       CASE WHEN m.category_budget IS NOT NULL
            THEN ROUND(m.total_spent * 100 / m.category_budget, 2)
       END                               AS percent_used,
       ROUND(
           m.total_spent * 100
           / NULLIF(SUM(m.total_spent) OVER (PARTITION BY m.user_id, m.period_month), 0),
           2
       )                                 AS share_of_month,
       m.transaction_count
  FROM merged m
  JOIN finance.users u      ON u.id = m.user_id
  JOIN finance.categories c ON c.id = m.category_id;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app, y antes rechazar o aprobar todo pendiente:
-- al borrar la columna, un pendiente pasaría a contar en los reportes sin haber
-- movido nunca el saldo.
--
-- Las dos vistas y la función vuelven a su versión anterior: sus CREATE OR
-- REPLACE están en el schema.sql previo a FA-76,
--   git show ff64d87:docs/database/schema.sql
-- y van primero, porque DROP COLUMN falla mientras las vistas lean status.
--
-- BEGIN;
-- (CREATE OR REPLACE de v_monthly_spending, v_monthly_spending_by_category y
--  sync_account_balances, copiados de ese archivo)
-- DROP INDEX finance.ix_transactions_pending;
-- ALTER TABLE finance.transactions DROP COLUMN status;
-- COMMIT;
-- -----------------------------------------------------------------------------
