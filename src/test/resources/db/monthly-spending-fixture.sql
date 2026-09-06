-- Escenario fijo de MonthlySpendingR2dbcAdapterIT. Fechas absolutas a proposito: las aserciones
-- del test comparan valores exactos y no pueden depender del mes en que se corra la suite.
--
-- Usuario 1 (zona America/Bogota, base COP):
--   2025-12  50.000 gastados, sin meta          <- fuera del rango que pide el test
--   2026-01 125.000 gastados en 2 movimientos, meta 200.000
--           el segundo cae el 31 a las 21:30 en Bogota (01-feb 02:30 UTC): debe contar en enero
--   2026-02 300.000 gastados, sin meta          <- ademas un INCOME que la vista debe ignorar
--   2026-03 sin gastos, meta 500.000            <- fila que existe solo por la meta
-- Usuario 2: un gasto en 2026-01 que jamas debe aparecer en la consulta del usuario 1.

SET search_path TO finance, public;

INSERT INTO finance.users (id, email, password_hash, first_name, base_currency_code, timezone) VALUES
    ('10000000-0000-7000-8000-000000000001', 'uno@financeapp.test', 'hash-de-prueba', 'Uno', 'COP', 'America/Bogota'),
    ('10000000-0000-7000-8000-000000000002', 'dos@financeapp.test', 'hash-de-prueba', 'Dos', 'COP', 'America/Bogota');

INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance) VALUES
    ('20000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001', 'Efectivo', 'CASH', 'COP', 0),
    ('20000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000002', 'Efectivo', 'CASH', 'COP', 0);

INSERT INTO finance.categories (id, user_id, name, applies_to) VALUES
    ('30000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001', 'Mercado', 'EXPENSE'),
    ('30000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000001', 'Salario', 'INCOME'),
    ('30000000-0000-7000-8000-000000000003', '10000000-0000-7000-8000-000000000002', 'Mercado', 'EXPENSE');

INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate,
     amount_base, description, occurred_at) VALUES
    ('40000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001',
     '20000000-0000-7000-8000-000000000001', '30000000-0000-7000-8000-000000000001',
     'EXPENSE',  50000, 'COP', 1,  50000, 'Diciembre',        TIMESTAMPTZ '2025-12-05 08:00:00-05'),
    ('40000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000001',
     '20000000-0000-7000-8000-000000000001', '30000000-0000-7000-8000-000000000001',
     'EXPENSE', 100000, 'COP', 1, 100000, 'Enero',            TIMESTAMPTZ '2026-01-10 08:00:00-05'),
    ('40000000-0000-7000-8000-000000000003', '10000000-0000-7000-8000-000000000001',
     '20000000-0000-7000-8000-000000000001', '30000000-0000-7000-8000-000000000001',
     'EXPENSE',  25000, 'COP', 1,  25000, 'Ultimo dia enero', TIMESTAMPTZ '2026-01-31 21:30:00-05'),
    ('40000000-0000-7000-8000-000000000004', '10000000-0000-7000-8000-000000000001',
     '20000000-0000-7000-8000-000000000001', '30000000-0000-7000-8000-000000000001',
     'EXPENSE', 300000, 'COP', 1, 300000, 'Febrero',          TIMESTAMPTZ '2026-02-14 12:00:00-05'),
    ('40000000-0000-7000-8000-000000000005', '10000000-0000-7000-8000-000000000001',
     '20000000-0000-7000-8000-000000000001', '30000000-0000-7000-8000-000000000002',
     'INCOME', 4500000, 'COP', 1, 4500000, 'Salario febrero', TIMESTAMPTZ '2026-02-01 09:00:00-05'),
    ('40000000-0000-7000-8000-000000000006', '10000000-0000-7000-8000-000000000002',
     '20000000-0000-7000-8000-000000000002', '30000000-0000-7000-8000-000000000003',
     'EXPENSE', 777000, 'COP', 1, 777000, 'Del otro usuario', TIMESTAMPTZ '2026-01-15 10:00:00-05');

INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code) VALUES
    ('50000000-0000-7000-8000-000000000001', '10000000-0000-7000-8000-000000000001',
     DATE '2026-01-01', NULL, 200000, 'COP'),
    ('50000000-0000-7000-8000-000000000002', '10000000-0000-7000-8000-000000000001',
     DATE '2026-03-01', NULL, 500000, 'COP'),
    -- Meta por categoria: la vista global la ignora, solo la usa v_monthly_spending_by_category.
    ('50000000-0000-7000-8000-000000000003', '10000000-0000-7000-8000-000000000001',
     DATE '2026-02-01', '30000000-0000-7000-8000-000000000001', 150000, 'COP');
