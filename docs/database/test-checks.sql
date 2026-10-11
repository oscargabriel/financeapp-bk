-- =============================================================================
-- financeapp-bk : verificación del escenario de pruebas
--
-- Solo lee. Se puede correr cuantas veces se quiera sobre los datos que dejó
-- test-data.sql, o después de tocar movimientos a mano para ver el efecto.
--
-- Ejecutar:  psql -U postgres -d financeapp -f docs/database/test-checks.sql
-- =============================================================================

\set ON_ERROR_STOP on
SET search_path TO finance, public;
\set uid '10000000-0000-7000-8000-000000000001'
\pset null '·'

\echo ''
\echo '=== 1. Saldos y cupo ==='
SELECT a.name,
       a.type,
       a.currency_code                     AS moneda,
       a.initial_balance                   AS inicial,
       a.current_balance                   AS saldo,
       a.credit_limit + a.current_balance  AS cupo_disponible
  FROM finance.accounts a
 WHERE a.user_id = :'uid'::uuid AND a.deleted_at IS NULL
 ORDER BY a.type, a.name;

\echo ''
\echo '=== 2. El saldo del trigger coincide con el derivado de los movimientos (coincide debe ser t) ==='
-- Solo los CONFIRMED, programados incluidos: el trigger no aplica los PENDING. Es la regla de
-- check-saldos.sql, que hace lo mismo sobre todas las cuentas y falla si alguna no cuadra (FA-29).
WITH derivado AS (
    SELECT a.id,
           a.name,
           a.current_balance,
           a.initial_balance
           + COALESCE((SELECT SUM(finance.balance_delta(t.type, t.amount))
                         FROM finance.transactions t
                        WHERE t.account_id = a.id AND t.status = 'CONFIRMED'), 0)
           + COALESCE((SELECT SUM(COALESCE(t.destination_amount, t.amount))
                         FROM finance.transactions t
                        WHERE t.destination_account_id = a.id AND t.status = 'CONFIRMED'), 0)
             AS esperado
      FROM finance.accounts a
     WHERE a.user_id = :'uid'::uuid
)
SELECT name, current_balance, esperado, current_balance = esperado AS coincide
  FROM derivado ORDER BY name;

\echo ''
\echo '=== 3. Gasto mensual contra la meta ==='
SELECT to_char(period_month, 'YYYY-MM') AS mes,
       total_spent   AS gastado,
       budget_amount AS meta,
       remaining     AS restante,
       percent_used  AS "% usado",
       transaction_count AS movimientos
  FROM finance.v_monthly_spending
 WHERE user_id = :'uid'::uuid
 ORDER BY period_month;

\echo ''
\echo '=== 4. Gasto del mes en curso por categoría ==='
SELECT category_name       AS categoria,
       total_spent         AS gastado,
       category_budget     AS tope,
       remaining           AS restante,
       percent_used        AS "% del tope",
       share_of_month      AS "% del mes",
       transaction_count   AS movimientos
  FROM finance.v_monthly_spending_by_category
 WHERE user_id = :'uid'::uuid
   AND period_month = date_trunc('month', now() AT TIME ZONE 'America/Bogota')::date
 ORDER BY total_spent DESC, category_name;

\echo ''
\echo '=== 5. Categorías que se pasaron del tope este mes ==='
SELECT category_name AS categoria, total_spent AS gastado, category_budget AS tope,
       total_spent - category_budget AS exceso
  FROM finance.v_monthly_spending_by_category
 WHERE user_id = :'uid'::uuid
   AND period_month = date_trunc('month', now() AT TIME ZONE 'America/Bogota')::date
   AND category_budget IS NOT NULL
   AND total_spent > category_budget
 ORDER BY exceso DESC;

\echo ''
\echo '=== 6. Últimos 10 movimientos ==='
SELECT to_char(t.occurred_at AT TIME ZONE u.timezone, 'YYYY-MM-DD HH24:MI') AS fecha,
       t.type          AS tipo,
       t.description   AS descripcion,
       c.name          AS categoria,
       o.name          AS cuenta,
       d.name          AS destino,
       t.amount        AS monto,
       t.currency_code AS moneda,
       t.origin        AS canal
  FROM finance.transactions t
  JOIN finance.users u    ON u.id = t.user_id
  JOIN finance.accounts o ON o.id = t.account_id
  LEFT JOIN finance.accounts d   ON d.id = t.destination_account_id
  LEFT JOIN finance.categories c ON c.id = t.category_id
 WHERE t.user_id = :'uid'::uuid
 ORDER BY t.occurred_at DESC
 LIMIT 10;

\echo ''
\echo '=== 7. El gasto del último día a las 21:30 en Bogotá cae en el mes correcto ==='
SELECT t.description                                                        AS movimiento,
       t.occurred_at                                                        AS "instante UTC",
       to_char(t.occurred_at AT TIME ZONE u.timezone, 'YYYY-MM-DD HH24:MI') AS "hora Bogotá",
       to_char(date_trunc('month', t.occurred_at AT TIME ZONE u.timezone), 'YYYY-MM') AS "mes en la vista",
       to_char(date_trunc('month', t.occurred_at), 'YYYY-MM')               AS "mes si se ignora la zona"
  FROM finance.transactions t
  JOIN finance.users u ON u.id = t.user_id
 WHERE t.user_id = :'uid'::uuid AND t.description = 'Cena de fin de mes';
