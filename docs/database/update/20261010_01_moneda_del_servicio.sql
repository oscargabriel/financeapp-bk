-- =============================================================================
-- 2026-10-10 · 01 · Moneda del servicio en USD (FA-122)
--
-- amount_base pasa a ser el equivalente en USD de cada movimiento, y
-- exchange_rate la tasa USD→moneda de su fecha. Los fija un trigger, no la app:
-- finance.usd_rate decide la tasa (la más reciente <= la fecha o, si no hay, la
-- más antigua) y finance.in_currency la usan las vistas y los reportes para dar
-- los totales en la moneda de la persona (users.base_currency_code), que queda
-- solo de presentación.
--
-- TOCA FILAS: recalcula exchange_rate y amount_base de todos los movimientos.
-- Para quien solo usa COP los totales no cambian: en su moneda se suma amount.
--
-- En producción se aplica INMEDIATAMENTE DESPUÉS de desplegar la app de FA-122:
--   1. desplegar la app (con FA-120);
--   2. GET /api/exchange-rates?from=USD&to=COP&date=<hoy> contra el servicio,
--      para que exista la primera tasa USD→COP;
--   3. aplicar este script.
-- Entre 1 y 3 el reporte y el saldo fallan, porque la app ya llama a
-- finance.in_currency. Al revés sería peor: la app vieja sumaría dólares como
-- pesos sin dar error. Si hay movimientos de una moneda sin ninguna tasa, el
-- script aborta sin cambiar nada.
--
-- En local REINICIA LOS DATOS: modifica finance.transactions y test-data.sql
-- cambia. Después de aplicarlo, correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261010_01_moneda_del_servicio.sql
-- =============================================================================

BEGIN;

DO $$
DECLARE
    v_faltantes TEXT;
BEGIN
    SELECT string_agg(DISTINCT t.currency_code, ', ')
      INTO v_faltantes
      FROM finance.transactions t
     WHERE t.currency_code <> 'USD'
       AND NOT EXISTS (SELECT 1
                         FROM finance.exchange_rates r
                        WHERE r.from_currency_code = 'USD'
                          AND r.to_currency_code = t.currency_code);
    IF v_faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'No hay ninguna tasa USD→% en exchange_rates. Pedir GET /api/exchange-rates?from=USD&to=<moneda>&date=<hoy> contra el servicio y volver a aplicar.', v_faltantes;
    END IF;
END;
$$;

-- La tasa USD→p_currency de una fecha: la fila más reciente <= p_on o, si no
-- hay ninguna, la más antigua guardada (FA-122: el proveedor no tiene histórico,
-- y el pasado anterior a la primera tasa usa esa). El dólar vale 1. Sin ninguna
-- fila del par no devuelve nada. La usan el trigger que congela la tasa de cada
-- movimiento, la conversión de los reportes y GET /api/exchange-rates, para que
-- los tres apliquen la misma regla.
CREATE OR REPLACE FUNCTION finance.usd_rate(p_currency TEXT, p_on DATE)
RETURNS TABLE (rate NUMERIC, rate_date DATE)
LANGUAGE sql
STABLE
AS $$
    SELECT c.rate, c.rate_date
      FROM (SELECT 1::NUMERIC AS rate, p_on AS rate_date, 0 AS prioridad
             WHERE p_currency = 'USD'
            UNION ALL
            (SELECT r.rate, r.rate_date, 1
               FROM finance.exchange_rates r
              WHERE r.from_currency_code = 'USD'
                AND r.to_currency_code = p_currency
                AND r.rate_date <= p_on
              ORDER BY r.rate_date DESC
              LIMIT 1)
            UNION ALL
            (SELECT r.rate, r.rate_date, 2
               FROM finance.exchange_rates r
              WHERE r.from_currency_code = 'USD'
                AND r.to_currency_code = p_currency
              ORDER BY r.rate_date
              LIMIT 1)) c
     ORDER BY c.prioridad
     LIMIT 1;
$$;

-- Un movimiento expresado en la moneda p_target, la de la persona que lo ve
-- (FA-122): su monto tal cual si ya está en esa moneda, su equivalente en USD si
-- la persona ve dólares, y si no, ese equivalente por la tasa USD→p_target de su
-- fecha. Lo que ya está en la moneda de la persona no pasa por el dólar, así no
-- arrastra el redondeo de ida y vuelta. NULL si p_target no tiene ninguna tasa.
CREATE OR REPLACE FUNCTION finance.in_currency(p_target TEXT, p_currency TEXT, p_amount NUMERIC,
                                               p_amount_base NUMERIC, p_on DATE)
RETURNS NUMERIC
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
               WHEN p_currency = p_target THEN p_amount
               WHEN p_target = 'USD' THEN p_amount_base
               ELSE round(p_amount_base * (SELECT u.rate FROM finance.usd_rate(p_target, p_on) u), 4)
           END;
$$;

-- Recalcular lo existente antes de crear el trigger, que en un UPDATE sin cambio
-- de monto conservaría los valores viejos. El saldo no se mueve (amount no
-- cambia) y la fecha de modificación tampoco debe moverse.
ALTER TABLE finance.transactions DISABLE TRIGGER trg_transactions_sync_balance;
ALTER TABLE finance.transactions DISABLE TRIGGER trg_transactions_updated_at;

UPDATE finance.transactions t
   SET exchange_rate = c.rate,
       amount_base   = GREATEST(round(t.amount / c.rate, 4), 0.0001)
  FROM (SELECT m.id, r.rate
          FROM finance.transactions m
          JOIN finance.users u ON u.id = m.user_id
         CROSS JOIN LATERAL finance.usd_rate(m.currency_code, (m.occurred_at AT TIME ZONE u.timezone)::date) r) c
 WHERE c.id = t.id;

ALTER TABLE finance.transactions ENABLE TRIGGER trg_transactions_sync_balance;
ALTER TABLE finance.transactions ENABLE TRIGGER trg_transactions_updated_at;

-- Congela el equivalente en USD de cada movimiento (FA-122): exchange_rate es la
-- tasa USD→currency_code de su fecha local y amount_base, amount en dólares. Lo
-- que mande la aplicación en esas dos columnas se ignora. En UPDATE solo se
-- recalcula si cambian el monto, la moneda o la fecha: aprobar un pendiente o
-- cambiarle la descripción no le cambia la tasa. Sin ninguna tasa de la moneda
-- falla con FX001, que la aplicación traduce a un 502.
CREATE OR REPLACE FUNCTION finance.set_usd_equivalent()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_rate NUMERIC;
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.amount = OLD.amount
       AND NEW.currency_code = OLD.currency_code
       AND NEW.occurred_at = OLD.occurred_at THEN
        NEW.exchange_rate := OLD.exchange_rate;
        NEW.amount_base := OLD.amount_base;
        RETURN NEW;
    END IF;

    SELECT r.rate
      INTO v_rate
      FROM finance.users u
     CROSS JOIN LATERAL finance.usd_rate(NEW.currency_code, (NEW.occurred_at AT TIME ZONE u.timezone)::date) r
     WHERE u.id = NEW.user_id;

    IF v_rate IS NULL THEN
        RAISE EXCEPTION 'No hay tasa de cambio USD→% para el movimiento', NEW.currency_code
              USING ERRCODE = 'FX001';
    END IF;

    -- Un monto de menos de medio peso redondearía a cero, y ck_transactions_amount_base exige > 0.
    NEW.exchange_rate := v_rate;
    NEW.amount_base := GREATEST(round(NEW.amount / v_rate, 4), 0.0001);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_usd_equivalent
    BEFORE INSERT OR UPDATE ON finance.transactions
    FOR EACH ROW EXECUTE FUNCTION finance.set_usd_equivalent();

CREATE OR REPLACE VIEW finance.v_monthly_spending AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           SUM(finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                 (t.occurred_at AT TIME ZONE u.timezone)::date))     AS total_spent,
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

COMMENT ON VIEW finance.v_monthly_spending IS 'Una fila por usuario y mes: cuánto gastó, cuál era la meta, cuánto le queda y qué porcentaje consumió.';


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

COMMENT ON VIEW finance.v_monthly_spending_by_category IS 'Desglose del gasto del mes por categoría, con su tope si existe y el peso porcentual sobre el total gastado en el mes.';

COMMENT ON TABLE  finance.exchange_rates IS 'Tasa de cambio por par de monedas y fecha. La app solo lee filas USD→X: toda conversión pasa por el dólar (finance.usd_rate).';
COMMENT ON COLUMN finance.users.base_currency_code IS 'Moneda en la que la persona ve sus totales y reportes. Solo es de presentación: lo guardado está en la moneda de cada cuenta y en USD (amount_base), y cambiarla no reescribe nada (FA-122).';
COMMENT ON COLUMN finance.transactions.exchange_rate IS 'Tasa USD→currency_code de la fecha del movimiento con la que se calculó amount_base (1 si es USD). La fija trg_transactions_usd_equivalent: la aplicación nunca la escribe.';
COMMENT ON COLUMN finance.transactions.amount_base IS 'amount en USD, la moneda del servicio: amount / exchange_rate. Es la base común entre monedas; los reportes la convierten a la moneda de la persona con finance.in_currency. La fija trg_transactions_usd_equivalent.';
COMMENT ON COLUMN finance.budgets.amount IS 'Expresado en la moneda en que la persona ve sus totales (users.base_currency_code), la misma de total_spent en las vistas.';

COMMIT;


-- =============================================================================
-- Reversión. Revertir antes la app: la de FA-122 llama a finance.in_currency y
-- finance.usd_rate. Antes de FA-122 todo movimiento estaba en la moneda base de
-- su usuario, así que amount_base vuelve a ser amount y la tasa, 1. Un
-- movimiento creado después en otra moneda (FA-51) no tendría vuelta: revisar
-- que no haya ninguno antes de revertir.
--
-- BEGIN;
-- DROP TRIGGER trg_transactions_usd_equivalent ON finance.transactions;
-- DROP FUNCTION finance.set_usd_equivalent();
--
-- ALTER TABLE finance.transactions DISABLE TRIGGER trg_transactions_sync_balance;
-- ALTER TABLE finance.transactions DISABLE TRIGGER trg_transactions_updated_at;
-- UPDATE finance.transactions t
--    SET exchange_rate = 1,
--        amount_base   = t.amount
--   FROM finance.users u
--  WHERE u.id = t.user_id
--    AND t.currency_code = u.base_currency_code;
-- ALTER TABLE finance.transactions ENABLE TRIGGER trg_transactions_sync_balance;
-- ALTER TABLE finance.transactions ENABLE TRIGGER trg_transactions_updated_at;
--
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
--
-- COMMENT ON VIEW finance.v_monthly_spending IS 'Una fila por usuario y mes: cuánto gastó, cuál era la meta, cuánto le queda y qué porcentaje consumió.';
--
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
-- COMMENT ON VIEW finance.v_monthly_spending_by_category IS 'Desglose del gasto del mes por categoría, con su tope si existe y el peso porcentual sobre el total gastado en el mes.';
--
-- DROP FUNCTION finance.in_currency(TEXT, TEXT, NUMERIC, NUMERIC, DATE);
-- DROP FUNCTION finance.usd_rate(TEXT, DATE);
--
-- COMMENT ON TABLE  finance.exchange_rates IS 'Tasa de cambio por par de monedas y fecha. Se consulta la fila con rate_date más reciente <= fecha del movimiento.';
-- COMMENT ON COLUMN finance.users.base_currency_code IS 'Moneda en la que se consolidan los reportes del usuario (columna amount_base de transactions).';
-- COMMENT ON COLUMN finance.transactions.exchange_rate IS NULL;
-- COMMENT ON COLUMN finance.transactions.amount_base IS 'amount convertido a la moneda base del usuario. Es la columna que suman los reportes, para que monedas distintas sean comparables.';
-- COMMENT ON COLUMN finance.budgets.amount IS 'Expresado en la moneda base del usuario, que es la unidad de amount_base en transactions.';
-- COMMIT;
