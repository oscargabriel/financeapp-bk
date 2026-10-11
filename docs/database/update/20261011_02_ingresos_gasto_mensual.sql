-- =============================================================================
-- 2026-10-11 · 02 · Ingresos del mes en v_monthly_spending (FA-132)
--
-- La vista agrega total_income: la suma de los INCOME confirmados y ya
-- ocurridos del mes, en la moneda de la persona, con el mismo WHERE que los
-- gastos. Un mes con solo ingresos pasa a tener fila, con total_spent 0 y la
-- meta en NULL. transaction_count sigue contando solo los EXPENSE.
--
-- total_income va al final: CREATE OR REPLACE VIEW solo admite columnas nuevas
-- en esa posición. Solo reemplaza la vista: no toca tablas, no cambia saldos ni
-- reinicia los datos locales.
--
-- En producción se aplica ANTES de desplegar la app de FA-132: la app nueva lee
-- total_income. La app vieja con la vista nueva sigue funcionando: no nombra la
-- columna.
--
--   psql -U <usuario> -d financeapp -f 20261011_02_ingresos_gasto_mensual.sql
-- =============================================================================

BEGIN;

CREATE OR REPLACE VIEW finance.v_monthly_spending AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                 (t.occurred_at AT TIME ZONE u.timezone)::date))
               FILTER (WHERE t.type = 'EXPENSE')                            AS total_spent,
           SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                 (t.occurred_at AT TIME ZONE u.timezone)::date))
               FILTER (WHERE t.type = 'INCOME')                             AS total_income,
           COUNT(*) FILTER (WHERE t.type = 'EXPENSE')                       AS transaction_count
      FROM finance.transactions t
      JOIN finance.users u ON u.id = t.user_id
     WHERE t.type IN ('EXPENSE', 'INCOME')
       AND t.status = 'CONFIRMED'
       AND t.occurred_at <= now()
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
       COALESCE(s.transaction_count, 0)             AS transaction_count,
       COALESCE(s.total_income, 0)                  AS total_income
  FROM spent s
  FULL OUTER JOIN budget b
    ON b.user_id = s.user_id
   AND b.period_month = s.period_month
  JOIN finance.users u
    ON u.id = COALESCE(s.user_id, b.user_id);

COMMENT ON VIEW finance.v_monthly_spending IS 'Una fila por usuario y mes con gastos, ingresos o meta: cuánto gastó, cuánto ingresó, cuál era la meta, cuánto le queda y qué porcentaje consumió.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FA-132 lee total_income.
-- CREATE OR REPLACE no puede quitar una columna, así que se borra y se recrea;
-- ninguna otra vista depende de esta.
--
-- BEGIN;
-- DROP VIEW finance.v_monthly_spending;
-- CREATE VIEW finance.v_monthly_spending AS
-- WITH spent AS (
--     SELECT t.user_id,
--            date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
--            SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
--                                  (t.occurred_at AT TIME ZONE u.timezone)::date))     AS total_spent,
--            COUNT(*)                                                         AS transaction_count
--       FROM finance.transactions t
--       JOIN finance.users u ON u.id = t.user_id
--      WHERE t.type = 'EXPENSE'
--        AND t.status = 'CONFIRMED'
--        AND t.occurred_at <= now()
--      GROUP BY t.user_id, 2
-- ),
-- budget AS (
--     SELECT b.user_id,
--            b.period_month,
--            b.amount AS budget_amount
--       FROM finance.budgets b
--      WHERE b.category_id IS NULL
-- )
-- SELECT COALESCE(s.user_id, b.user_id)               AS user_id,
--        COALESCE(s.period_month, b.period_month)     AS period_month,
--        u.base_currency_code                         AS currency_code,
--        COALESCE(s.total_spent, 0)                   AS total_spent,
--        b.budget_amount,
--        b.budget_amount - COALESCE(s.total_spent, 0) AS remaining,
--        CASE WHEN b.budget_amount IS NOT NULL
--             THEN ROUND(COALESCE(s.total_spent, 0) * 100 / b.budget_amount, 2)
--        END                                          AS percent_used,
--        COALESCE(s.transaction_count, 0)             AS transaction_count
--   FROM spent s
--   FULL OUTER JOIN budget b
--     ON b.user_id = s.user_id
--    AND b.period_month = s.period_month
--   JOIN finance.users u
--     ON u.id = COALESCE(s.user_id, b.user_id);
-- COMMENT ON VIEW finance.v_monthly_spending IS 'Una fila por usuario y mes: cuánto gastó, cuál era la meta, cuánto le queda y qué porcentaje consumió.';
-- COMMIT;
-- -----------------------------------------------------------------------------
