-- =============================================================================
-- 2026-10-09 · 03 · Movimientos programados (FA-106)
--
-- Un movimiento confirmado con fecha posterior a now() está programado: no
-- cuenta en el saldo vigente ni en el gasto mensual hasta su fecha. Nada lo
-- "activa": todo se calcula al leer, así que funciona con el servicio apagado.
--
-- current_balance sigue incluyendo todo (el trigger no cambia). La app lee el
-- saldo vigente como current_balance - finance.scheduled_balance_delta(id).
-- Las dos vistas de gasto mensual dejan fuera lo que aún no ocurrió.
--
-- No toca filas: ningún saldo guardado cambia al aplicarlo.
--
-- En producción se aplica ANTES de desplegar la app de FA-106: la app nueva
-- llama a scheduled_balance_delta. La app vieja con la base nueva sigue
-- funcionando: no llama a la función, y las vistas solo dejan de sumar lo
-- que tenga fecha futura.
--
-- En local REINICIA LOS DATOS: test-data.sql cambia. Después de aplicarlo,
-- correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261009_03_movimientos_programados.sql
-- =============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION finance.scheduled_balance_delta(p_account_id UUID)
RETURNS NUMERIC
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(SUM(efecto), 0)
      FROM (SELECT finance.balance_delta(t.type, t.amount) AS efecto
              FROM finance.transactions t
             WHERE t.account_id = p_account_id
               AND t.status = 'CONFIRMED'
               AND t.occurred_at > now()
            UNION ALL
            SELECT COALESCE(t.destination_amount, t.amount)
              FROM finance.transactions t
             WHERE t.destination_account_id = p_account_id
               AND t.status = 'CONFIRMED'
               AND t.occurred_at > now()) AS programados;
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
       AND t.occurred_at <= now()
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

COMMENT ON COLUMN finance.accounts.current_balance IS 'initial_balance más el efecto de todos los movimientos confirmados, también los programados (fecha futura). El saldo vigente que ve el usuario es current_balance - finance.scheduled_balance_delta(id). Lo mantienen los triggers trg_transactions_sync_balance y trg_accounts_shift_balance; la aplicación NUNCA lo escribe directamente. En una tarjeta de crédito un valor negativo es la deuda, y el cupo disponible es credit_limit más el saldo vigente.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Revertir antes la app: la app de FA-106 llama a la función. Las
-- vistas vuelven a sumar lo programado.
--
-- BEGIN;
-- CREATE OR REPLACE VIEW finance.v_monthly_spending AS
-- WITH spent AS (
--     SELECT t.user_id,
--            date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
--            SUM(t.amount_base)                                               AS total_spent,
--            COUNT(*)                                                         AS transaction_count
--       FROM finance.transactions t
--       JOIN finance.users u ON u.id = t.user_id
--      WHERE t.type = 'EXPENSE'
--        AND t.status = 'CONFIRMED'
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
--
-- CREATE OR REPLACE VIEW finance.v_monthly_spending_by_category AS
-- WITH spent AS (
--     SELECT t.user_id,
--            date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
--            t.category_id,
--            SUM(t.amount_base)                                               AS total_spent,
--            COUNT(*)                                                         AS transaction_count
--       FROM finance.transactions t
--       JOIN finance.users u ON u.id = t.user_id
--      WHERE t.type = 'EXPENSE'
--        AND t.status = 'CONFIRMED'
--      GROUP BY t.user_id, 2, t.category_id
-- ),
-- budget AS (
--     SELECT b.user_id,
--            b.period_month,
--            b.category_id,
--            b.amount AS category_budget
--       FROM finance.budgets b
--      WHERE b.category_id IS NOT NULL
-- ),
-- merged AS (
--     SELECT COALESCE(s.user_id, b.user_id)           AS user_id,
--            COALESCE(s.period_month, b.period_month) AS period_month,
--            COALESCE(s.category_id, b.category_id)   AS category_id,
--            COALESCE(s.total_spent, 0)               AS total_spent,
--            COALESCE(s.transaction_count, 0)         AS transaction_count,
--            b.category_budget
--       FROM spent s
--       FULL OUTER JOIN budget b
--         ON b.user_id = s.user_id
--        AND b.period_month = s.period_month
--        AND b.category_id = s.category_id
-- )
-- SELECT m.user_id,
--        m.period_month,
--        m.category_id,
--        c.name                            AS category_name,
--        c.icon                            AS category_icon,
--        c.color                           AS category_color,
--        u.base_currency_code              AS currency_code,
--        m.total_spent,
--        m.category_budget,
--        m.category_budget - m.total_spent AS remaining,
--        CASE WHEN m.category_budget IS NOT NULL
--             THEN ROUND(m.total_spent * 100 / m.category_budget, 2)
--        END                               AS percent_used,
--        ROUND(
--            m.total_spent * 100
--            / NULLIF(SUM(m.total_spent) OVER (PARTITION BY m.user_id, m.period_month), 0),
--            2
--        )                                 AS share_of_month,
--        m.transaction_count
--   FROM merged m
--   JOIN finance.users u      ON u.id = m.user_id
--   JOIN finance.categories c ON c.id = m.category_id;
-- DROP FUNCTION finance.scheduled_balance_delta(UUID);
-- COMMENT ON COLUMN finance.accounts.current_balance IS 'Saldo vigente: initial_balance más el efecto de los movimientos. Lo mantienen los triggers trg_transactions_sync_balance y trg_accounts_shift_balance; la aplicación NUNCA lo escribe directamente. En una tarjeta de crédito un valor negativo es la deuda, y el cupo disponible es credit_limit + current_balance.';
-- COMMIT;
-- -----------------------------------------------------------------------------
