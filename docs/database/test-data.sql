-- =============================================================================
-- financeapp-bk : escenario de pruebas
--
-- Re-ejecutable: borra el usuario de pruebas con todo lo suyo y lo vuelve a
-- crear desde cero. Correrlo dos veces seguidas deja exactamente el mismo
-- estado, así que sirve para probar el modelo mientras se construye.
--
-- Las fechas son relativas al mes en curso, no fijas: el escenario siempre
-- tiene movimientos de "este mes" y "el mes pasado".
--
-- Ejecutar:  psql -U postgres -d financeapp -f docs/database/test-data.sql
-- Verificar: psql -U postgres -d financeapp -f docs/database/test-checks.sql
-- =============================================================================

\set ON_ERROR_STOP on
SET search_path TO finance, public;

\set uid    '10000000-0000-7000-8000-000000000001'
\set cash   '20000000-0000-7000-8000-000000000001'
\set debit  '20000000-0000-7000-8000-000000000002'
\set credit '20000000-0000-7000-8000-000000000003'
\set usd    '20000000-0000-7000-8000-000000000004'

BEGIN;

-- Borrado del escenario anterior. El resto cae en cascada desde users.
DELETE FROM finance.users WHERE email = 'prueba@financeapp.local';


-- -----------------------------------------------------------------------------
-- Usuario
-- -----------------------------------------------------------------------------
INSERT INTO finance.users
    (id, email, password_hash, first_name, last_name, phone, birth_date,
     telegram_chat_id, base_currency_code, timezone)
VALUES
    (:'uid'::uuid, 'prueba@financeapp.local', '$2a$10$hashDePruebaNoEsUnaClaveReal',
     'Oscar', 'Zambrano', '3001234567', DATE '1995-03-14',
     123456789, 'COP', 'America/Bogota');


-- -----------------------------------------------------------------------------
-- Categorías: copia de la semilla, igual que hace el registro en la aplicación
-- -----------------------------------------------------------------------------
INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), :'uid'::uuid, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 WHERE d.is_active;

-- Una categoría propia, para probar que conviven con las del sistema.
INSERT INTO finance.categories (id, user_id, name, applies_to, icon, color, sort_order)
VALUES (gen_random_uuid(), :'uid'::uuid, 'Gimnasio', 'EXPENSE', 'dumbbell', '#00695C', 300);


-- -----------------------------------------------------------------------------
-- Cuentas
-- -----------------------------------------------------------------------------
INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance)
VALUES
    (:'cash'::uuid,  :'uid'::uuid, 'Efectivo',    'CASH',    'COP',  500000),
    (:'debit'::uuid, :'uid'::uuid, 'Bancolombia', 'DEBIT',   'COP', 2000000),
    (:'usd'::uuid,   :'uid'::uuid, 'Ahorros USD', 'SAVINGS', 'USD',    1200);

INSERT INTO finance.accounts
    (id, user_id, name, type, currency_code, initial_balance,
     credit_limit, statement_day, payment_due_day)
VALUES
    (:'credit'::uuid, :'uid'::uuid, 'Visa', 'CREDIT', 'COP', 0, 5000000, 15, 5);


-- -----------------------------------------------------------------------------
-- Tasa de cambio del día
-- -----------------------------------------------------------------------------
INSERT INTO finance.exchange_rates (from_currency_code, to_currency_code, rate, rate_date)
VALUES ('USD', 'COP', 4100.0000000000, CURRENT_DATE)
ON CONFLICT (from_currency_code, to_currency_code, rate_date) DO UPDATE SET rate = EXCLUDED.rate;


-- -----------------------------------------------------------------------------
-- Gastos e ingresos del mes en curso
--
-- El offset se suma al primer día del mes en hora de Bogotá. El último
-- movimiento cae el ultimo día a las 21:30 locales: en UTC ya es del mes
-- siguiente, y sirve para comprobar que las vistas lo cuentan en este mes.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, m.account_id, c.id, m.type, m.amount, 'COP',
       1, m.amount, m.description, (r.m0 + m.desfase) AT TIME ZONE 'America/Bogota', m.origin
  FROM (VALUES
        (:'debit'::uuid,  'Vivienda',        'EXPENSE', 1200000::numeric, 'Arriendo',            INTERVAL '0 day 8 hours',   'WEB'),
        (:'debit'::uuid,  'Salario',         'INCOME',  4500000::numeric, 'Salario quincena 1',  INTERVAL '0 day 9 hours',   'WEB'),
        (:'credit'::uuid, 'Mercado',         'EXPENSE',   85000::numeric, 'Carne y verduras',    INTERVAL '1 day 10 hours',  'TELEGRAM'),
        (:'credit'::uuid, 'Restaurantes',    'EXPENSE',   38000::numeric, 'Almuerzo',            INTERVAL '3 day 13 hours',  'TELEGRAM'),
        (:'cash'::uuid,   'Transporte',      'EXPENSE',   15000::numeric, 'Taxi',                INTERVAL '4 day 7 hours',   'TELEGRAM'),
        (:'debit'::uuid,  'Servicios',       'EXPENSE',  180000::numeric, 'Luz y agua',          INTERVAL '7 day 11 hours',  'WEB'),
        (:'cash'::uuid,   'Mercado',         'EXPENSE',   42500::numeric, 'Fruta',               INTERVAL '11 day 18 hours', 'TELEGRAM'),
        (:'debit'::uuid,  'Freelance',       'INCOME',   800000::numeric, 'Proyecto externo',    INTERVAL '14 day 16 hours', 'WEB'),
        (:'credit'::uuid, 'Entretenimiento', 'EXPENSE',   60000::numeric, 'Cine',                INTERVAL '19 day 20 hours', 'WEB'),
        (:'debit'::uuid,  'Salud',           'EXPENSE',   90000::numeric, 'Consulta médica',     INTERVAL '21 day 9 hours',  'WEB'),
        (:'credit'::uuid, 'Suscripciones',   'EXPENSE',   25000::numeric, 'Streaming',           INTERVAL '24 day 6 hours',  'IMPORT'),
        (:'cash'::uuid,   'Gimnasio',        'EXPENSE',  120000::numeric, 'Mensualidad',         INTERVAL '25 day 19 hours', 'WEB')
       ) AS m(account_id, category_name, type, amount, description, desfase, origin)
 CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota') AS m0) r
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = m.category_name;

-- Gasto del último día del mes a las 21:30 en Bogotá (frontera de mes).
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'cash'::uuid, c.id, 'EXPENSE', 50000, 'COP',
       1, 50000, 'Cena de fin de mes',
       (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
        + INTERVAL '1 month' - INTERVAL '2 hours 30 minutes') AT TIME ZONE 'America/Bogota',
       'TELEGRAM'
  FROM finance.categories c
 WHERE c.user_id = :'uid'::uuid AND c.name = 'Restaurantes';


-- -----------------------------------------------------------------------------
-- Transferencias del mes en curso
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, destination_amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'debit'::uuid, :'cash'::uuid, 'TRANSFER',
       300000, NULL, 'COP', 1, 300000, 'Retiro de cajero',
       (r.m0 + INTERVAL '2 day 12 hours') AT TIME ZONE 'America/Bogota', 'WEB'
  FROM (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota') AS m0) r;

-- Transferencia entre monedas distintas: salen 100 USD y entran 410.000 COP.
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, destination_amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'usd'::uuid, :'debit'::uuid, 'TRANSFER',
       100, 410000, 'USD', 4100, 410000, 'Cambio de dólares',
       (r.m0 + INTERVAL '9 day 15 hours') AT TIME ZONE 'America/Bogota', 'WEB'
  FROM (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota') AS m0) r;


-- -----------------------------------------------------------------------------
-- Mes anterior, para tener con qué comparar
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, m.account_id, c.id, m.type, m.amount, 'COP',
       1, m.amount, m.description, (r.m0 + m.desfase) AT TIME ZONE 'America/Bogota', 'WEB'
  FROM (VALUES
        (:'debit'::uuid,  'Vivienda',     'EXPENSE', 1200000::numeric, 'Arriendo',           INTERVAL '0 day 8 hours'),
        (:'debit'::uuid,  'Salario',      'INCOME',  4500000::numeric, 'Salario',            INTERVAL '0 day 9 hours'),
        (:'credit'::uuid, 'Mercado',      'EXPENSE',  320000::numeric, 'Mercado del mes',    INTERVAL '5 day 11 hours'),
        (:'credit'::uuid, 'Restaurantes', 'EXPENSE',   95000::numeric, 'Cena',               INTERVAL '12 day 20 hours'),
        (:'cash'::uuid,   'Transporte',   'EXPENSE',   40000::numeric, 'Buses',              INTERVAL '18 day 7 hours'),
        (:'debit'::uuid,  'Servicios',    'EXPENSE',  165000::numeric, 'Luz y agua',         INTERVAL '22 day 11 hours')
       ) AS m(account_id, category_name, type, amount, description, desfase)
 CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                    - INTERVAL '1 month' AS m0) r
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = m.category_name;


-- -----------------------------------------------------------------------------
-- Hace dos meses: gastos e ingresos, y a propósito SIN meta global
--
-- Es el único mes del escenario que no tiene budget con category_id IS NULL, así
-- que v_monthly_spending lo devuelve con budget_amount, remaining y percent_used
-- en NULL. Sirve para distinguir "no configuró meta" de "meta en cero", que no
-- son lo mismo. El ingreso comprueba de paso que la vista solo suma los EXPENSE.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, m.account_id, c.id, m.type, m.amount, 'COP',
       1, m.amount, m.description, (r.m0 + m.desfase) AT TIME ZONE 'America/Bogota', 'WEB'
  FROM (VALUES
        (:'debit'::uuid,  'Vivienda',  'EXPENSE', 1200000::numeric, 'Arriendo',          INTERVAL '0 day 8 hours'),
        (:'debit'::uuid,  'Salario',   'INCOME',  4500000::numeric, 'Salario',           INTERVAL '0 day 9 hours'),
        (:'cash'::uuid,   'Mercado',   'EXPENSE',  210000::numeric, 'Mercado del mes',   INTERVAL '6 day 10 hours'),
        (:'credit'::uuid, 'Transporte','EXPENSE',   35000::numeric, 'Buses',             INTERVAL '17 day 7 hours')
       ) AS m(account_id, category_name, type, amount, description, desfase)
 CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                    - INTERVAL '2 month' AS m0) r
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = m.category_name;


-- -----------------------------------------------------------------------------
-- Metas
--   * Global de este mes: 2.000.000
--   * Restaurantes se pasa del tope (88.000 sobre 80.000)
--   * Mes siguiente: solo meta, sin gastos todavía
--   * Hace dos meses: deliberadamente ausente, para el caso NULL
-- -----------------------------------------------------------------------------
INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code)
SELECT gen_random_uuid(), :'uid'::uuid, (r.m0 + b.mes)::date, NULL, b.amount, 'COP'
  FROM (VALUES
        (INTERVAL '0 month',  2000000::numeric),
        (INTERVAL '-1 month', 1800000::numeric),
        (INTERVAL '1 month',  2000000::numeric)
       ) AS b(mes, amount)
 CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota') AS m0) r;

INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code)
SELECT gen_random_uuid(), :'uid'::uuid, r.m0::date, c.id, b.amount, 'COP'
  FROM (VALUES
        ('Mercado',      150000::numeric),
        ('Restaurantes',  80000::numeric),
        ('Transporte',    60000::numeric),
        ('Viajes',       400000::numeric)
       ) AS b(category_name, amount)
 CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Bogota') AS m0) r
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = b.category_name;

COMMIT;

\echo ''
\echo 'Escenario de pruebas recreado para prueba@financeapp.local'
SELECT (SELECT count(*) FROM finance.accounts     WHERE user_id = :'uid'::uuid) AS cuentas,
       (SELECT count(*) FROM finance.categories   WHERE user_id = :'uid'::uuid) AS categorias,
       (SELECT count(*) FROM finance.transactions WHERE user_id = :'uid'::uuid) AS movimientos,
       (SELECT count(*) FROM finance.budgets      WHERE user_id = :'uid'::uuid) AS metas;
