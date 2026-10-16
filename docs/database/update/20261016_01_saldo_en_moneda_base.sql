-- =============================================================================
-- 2026-10-16 · 01 · El saldo de cada cuenta en la moneda de la persona (FIN-104)
--
-- Crea finance.v_account_balance_in_base: el saldo vigente de cada cuenta no
-- borrada en la moneda base de su dueño, con cada aporte convertido con la tasa
-- de su fecha. GET /api/reports/balance suma ahí las débito activas para dar
-- debitBalance.
--
-- Solo crea una vista: NO reinicia los datos de la base local. En producción va
-- ANTES de desplegar la app de FIN-104, que lee la vista en cada consulta de saldo.
--
--   psql -U <usuario> -d financeapp -f 20261016_01_saldo_en_moneda_base.sql
-- =============================================================================

BEGIN;

CREATE OR REPLACE VIEW finance.v_account_balance_in_base AS
SELECT a.id                 AS account_id,
       a.user_id,
       a.type               AS account_type,
       a.is_active,
       u.base_currency_code AS currency_code,
       finance.amount_in_currency(u.base_currency_code, a.currency_code, a.initial_balance,
                                  (a.created_at AT TIME ZONE u.timezone)::date)
           + COALESCE(m.efecto, 0) AS balance
  FROM finance.accounts a
  JOIN finance.users u ON u.id = a.user_id
  LEFT JOIN LATERAL (
      SELECT SUM(e.efecto) AS efecto
        FROM (SELECT finance.balance_delta(t.type,
                         finance.in_currency(u.base_currency_code, t.currency_code, t.amount,
                                             t.amount_base, (t.occurred_at AT TIME ZONE u.timezone)::date))
                         AS efecto
                FROM finance.transactions t
               WHERE t.account_id = a.id
                 AND t.status = 'CONFIRMED'
                 AND t.occurred_at <= now()
              UNION ALL
              SELECT CASE WHEN t.destination_amount IS NULL
                          THEN finance.in_currency(u.base_currency_code, t.currency_code, t.amount,
                                                   t.amount_base, (t.occurred_at AT TIME ZONE u.timezone)::date)
                          ELSE finance.amount_in_currency(u.base_currency_code, a.currency_code,
                                                          t.destination_amount,
                                                          (t.occurred_at AT TIME ZONE u.timezone)::date)
                     END
                FROM finance.transactions t
               WHERE t.destination_account_id = a.id
                 AND t.status = 'CONFIRMED'
                 AND t.occurred_at <= now()) AS e
  ) m ON TRUE
 WHERE a.deleted_at IS NULL;

COMMENT ON VIEW finance.v_account_balance_in_base IS 'Saldo vigente de cada cuenta no borrada en la moneda base de su dueño, con cada aporte convertido con la tasa de su fecha: el saldo inicial con la de la creación de la cuenta y cada movimiento con la suya.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión, DESPUÉS de revertir la app: la de FIN-104 falla sin la vista.
--
-- DROP VIEW finance.v_account_balance_in_base;
-- -----------------------------------------------------------------------------
