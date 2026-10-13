-- =============================================================================
-- 2026-10-12 · 02 · Metas en la moneda de la persona (FIN-29)
--
-- Cada meta guarda su monto en su propio currency_code, pero las vistas de gasto
-- mensual lo comparaban tal cual contra total_spent, que está en la moneda base
-- de la persona. Con FIN-29 la moneda base se puede cambiar desde el perfil, y
-- una meta en COP se habría leído como si estuviera en la moneda nueva.
--
-- finance.amount_in_currency convierte un monto de una moneda a otra con las
-- tasas USD→X de una fecha: tal cual si ya está en la moneda destino, si no
-- pasando por el dólar con un solo redondeo a 4 decimales. Las dos vistas la
-- usan con period_month, el día 1 del mes de la meta. budget_amount y
-- category_budget conservan su tipo, NUMERIC(18,4): CREATE OR REPLACE VIEW no
-- admite cambiarlo. Las metas guardadas no se reescriben.
--
-- No toca tablas: no cambia saldos ni reinicia los datos locales. En producción
-- puede aplicarse antes o después de desplegar la app de FIN-29, que no nombra
-- nada nuevo. Las metas de Neon están en la moneda de su persona: sus números
-- no cambian.
--
--   psql -U <usuario> -d financeapp -f 20261012_02_metas_en_moneda_de_la_persona.sql
-- =============================================================================

BEGIN;

-- p_amount en p_currency, llevado a p_target con las tasas USD→X de p_on (FIN-29).
-- NULL si alguna de las dos monedas no tiene ninguna tasa.
CREATE OR REPLACE FUNCTION finance.amount_in_currency(p_target TEXT, p_currency TEXT, p_amount NUMERIC,
                                                      p_on DATE)
RETURNS NUMERIC
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
               WHEN p_currency = p_target THEN p_amount
               ELSE round(p_amount / (SELECT d.rate FROM finance.usd_rate(p_currency, p_on) d)
                                   * (SELECT h.rate FROM finance.usd_rate(p_target, p_on) h), 4)
           END;
$$;

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
           finance.amount_in_currency(u.base_currency_code, b.currency_code, b.amount,
                                      b.period_month)::NUMERIC(18,4) AS budget_amount
      FROM finance.budgets b
      JOIN finance.users u ON u.id = b.user_id
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

CREATE OR REPLACE VIEW finance.v_monthly_spending_by_category AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           t.category_id,
           SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                 (t.occurred_at AT TIME ZONE u.timezone)::date))     AS total_spent,
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
           finance.amount_in_currency(u.base_currency_code, b.currency_code, b.amount,
                                      b.period_month)::NUMERIC(18,4) AS category_budget
      FROM finance.budgets b
      JOIN finance.users u ON u.id = b.user_id
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

COMMENT ON COLUMN finance.budgets.amount IS 'Expresado en currency_code, la moneda de la meta. Las vistas de gasto mensual lo convierten a la moneda de la persona con finance.amount_in_currency y la tasa del día 1 del mes (FIN-29).';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. La app no necesita la función: se puede revertir sin tocarla. Las
-- metas vuelven a compararse sin convertir.
--
-- BEGIN;
-- CREATE OR REPLACE VIEW finance.v_monthly_spending AS
-- WITH spent AS (
--     SELECT t.user_id,
--            date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
--            SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
--                                  (t.occurred_at AT TIME ZONE u.timezone)::date))
--                FILTER (WHERE t.type = 'EXPENSE')                            AS total_spent,
--            SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
--                                  (t.occurred_at AT TIME ZONE u.timezone)::date))
--                FILTER (WHERE t.type = 'INCOME')                             AS total_income,
--            COUNT(*) FILTER (WHERE t.type = 'EXPENSE')                       AS transaction_count
--       FROM finance.transactions t
--       JOIN finance.users u ON u.id = t.user_id
--      WHERE t.type IN ('EXPENSE', 'INCOME')
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
--        COALESCE(s.transaction_count, 0)             AS transaction_count,
--        COALESCE(s.total_income, 0)                  AS total_income
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
--            SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
--                                  (t.occurred_at AT TIME ZONE u.timezone)::date))     AS total_spent,
--            COUNT(*)                                                         AS transaction_count
--       FROM finance.transactions t
--       JOIN finance.users u ON u.id = t.user_id
--      WHERE t.type = 'EXPENSE'
--        AND t.status = 'CONFIRMED'
--        AND t.occurred_at <= now()
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
--
-- DROP FUNCTION finance.amount_in_currency(TEXT, TEXT, NUMERIC, DATE);
-- COMMENT ON COLUMN finance.budgets.amount IS 'Expresado en la moneda en que la persona ve sus totales (users.base_currency_code), la misma de total_spent en las vistas.';
-- COMMIT;
-- -----------------------------------------------------------------------------
