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

\set uid      '10000000-0000-7000-8000-000000000001'
\set inactivo '10000000-0000-7000-8000-000000000002'
\set cash     '20000000-0000-7000-8000-000000000001'
\set debit    '20000000-0000-7000-8000-000000000002'
\set credit   '20000000-0000-7000-8000-000000000003'
\set usd      '20000000-0000-7000-8000-000000000004'
\set nequi    '20000000-0000-7000-8000-000000000005'
\set davivienda '20000000-0000-7000-8000-000000000006'
\set cigarrillos '30000000-0000-7000-8000-000000000170'
\set pendientes          '10000000-0000-7000-8000-000000000003'
\set cuenta_pendientes   '20000000-0000-7000-8000-000000000007'
\set bolsillo_pendientes '20000000-0000-7000-8000-000000000008'
\set pendiente_gasto     '70000000-0000-7000-8000-000000000001'
\set pendiente_traslado  '70000000-0000-7000-8000-000000000002'
\set pendiente_rechazo   '70000000-0000-7000-8000-000000000003'
\set series              '10000000-0000-7000-8000-000000000004'
\set cuenta_atrasada     '20000000-0000-7000-8000-000000000009'
\set cuenta_series       '20000000-0000-7000-8000-000000000010'
\set cuenta_series_otra  '20000000-0000-7000-8000-000000000011'
\set serie_atrasada      '80000000-0000-7000-8000-000000000001'
\set cuotas              '10000000-0000-7000-8000-000000000005'
\set visa                '20000000-0000-7000-8000-000000000012'
\set visa_31             '20000000-0000-7000-8000-000000000013'
\set visa_sin_corte      '20000000-0000-7000-8000-000000000014'
\set ahorros_cuotas      '20000000-0000-7000-8000-000000000015'
\set compra_ajena        '90000000-0000-7000-8000-000000000001'

BEGIN;

-- Borrado del escenario anterior. El resto cae en cascada desde users.
DELETE FROM finance.users
 WHERE email IN ('prueba@financeapp.local', 'inactivo@financeapp.local', 'pendientes@financeapp.local',
                 'series@financeapp.local', 'cuotas@financeapp.local');

-- Los requests de alta de bruno/ crean un usuario nuevo en cada corrida: auth/ con el correo
-- registro-<timestamp>@bruno.local, monthly-spending/ con sin-datos-<timestamp>@bruno.local,
-- categories/ con categorias-<timestamp>@bruno.local, accounts/ con cuentas-<timestamp>@bruno.local,
-- transactions/ con transacciones-<timestamp>@bruno.local y reports/ con reportes-<timestamp>@bruno.local.
-- Sus cuentas y movimientos caen en cascada.
-- Recargar este escenario es lo que los limpia.
DELETE FROM finance.users WHERE email LIKE '%@bruno.local';

-- auth/registro-admitido-exacto lo registra en cada corrida: sin borrarlo, la siguiente daria 409.
DELETE FROM finance.users WHERE email = 'invitado@financeapp.local';

-- Lista de admitidos del alta (FA-103). El dominio deja registrar a todos los requests de alta de
-- bruno/, cuyo correo cambia en cada corrida; el correo exacto es el que prueba esa otra forma.
-- La demo agrega @front.local; la lista es compartida, por eso ON CONFLICT.
INSERT INTO finance.registration_allowlist (entry)
VALUES ('@bruno.local'), ('invitado@financeapp.local')
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
-- Usuario
-- -----------------------------------------------------------------------------
-- El hash es BCrypt cost 10 de la contrasena 'claveDePrueba123', y esta escrito aqui en vez
-- de generarse porque psql no sabe calcular BCrypt sin pgcrypto. Versionar el hash obliga a
-- documentar la clave en alguna parte, y este comentario es donde se busca; la copia que usan
-- los requests vive en bruno/.env como TEST_USER_PASSWORD para que ninguno la lleve incrustada.
-- Es un dato del escenario local, no una credencial: no abre nada fuera de esta base.
INSERT INTO finance.users
    (id, email, password_hash, first_name, last_name, phone, birth_date,
     telegram_chat_id, base_currency_code, timezone)
VALUES
    (:'uid'::uuid, 'prueba@financeapp.local',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Oscar', 'Zambrano', '3001234567', DATE '1995-03-14',
     123456789, 'COP', 'America/Bogota');


-- Misma contrasena que el usuario de arriba, y a proposito: lo unico que cambia entre los dos
-- es is_active, asi que un login que lo dejara entrar solo podria haber fallado en ese filtro.
-- Sin categorias ni movimientos: no existe para ningun otro request de la coleccion.
INSERT INTO finance.users
    (id, email, password_hash, first_name, base_currency_code, timezone, is_active)
VALUES
    (:'inactivo'::uuid, 'inactivo@financeapp.local',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Cuenta', 'COP', 'America/Bogota', FALSE);


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

-- La semilla no trae ninguna BOTH: sin esta, el filtro de bruno/categories/ no tendria como
-- probar que pedir EXPENSE o INCOME tambien devuelve las que sirven para los dos.
INSERT INTO finance.categories (id, user_id, name, applies_to, icon, color, sort_order)
VALUES (gen_random_uuid(), :'uid'::uuid, 'Ajustes', 'BOTH', 'sliders-horizontal', '#607D8B', 260);

-- Borrada logicamente: GET /api/categories no la puede devolver. Id fijo para que
-- bruno/categories/ pueda pedirla y comprobar el 404 del PATCH.
INSERT INTO finance.categories (id, user_id, name, applies_to, icon, color, sort_order, deleted_at)
VALUES (:'cigarrillos'::uuid, :'uid'::uuid, 'Cigarrillos', 'EXPENSE', 'flame', '#BF360C', 170, now());


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
     credit_limit, statement_day, payment_due_day, monthly_interest_rate)
VALUES
    (:'credit'::uuid, :'uid'::uuid, 'Visa', 'CREDIT', 'COP', 0, 5000000, 15, 5, 2.1);

-- Desactivada: GET /api/accounts solo la devuelve con includeInactive=true. Sin movimientos, asi
-- que su saldo vigente es el inicial.
INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance, is_active)
VALUES (:'nequi'::uuid, :'uid'::uuid, 'Nequi', 'DEBIT', 'COP', 80000, FALSE);

-- Borrada logicamente: GET /api/accounts no la devuelve nunca, ni con includeInactive=true. Id
-- fijo para que bruno/accounts/ pueda pedirla y comprobar el 404 del PATCH.
INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance, deleted_at)
VALUES (:'davivienda'::uuid, :'uid'::uuid, 'Davivienda', 'SAVINGS', 'COP', 150000, now());


-- -----------------------------------------------------------------------------
-- Tasa de cambio del día
-- -----------------------------------------------------------------------------
INSERT INTO finance.exchange_rates (from_currency_code, to_currency_code, rate, rate_date)
VALUES ('USD', 'COP', 4100.0000000000, CURRENT_DATE)
ON CONFLICT (from_currency_code, to_currency_code, rate_date) DO UPDATE SET rate = EXCLUDED.rate;


-- -----------------------------------------------------------------------------
-- Moneda inactiva
-- -----------------------------------------------------------------------------
-- La semilla solo trae monedas activas: sin esta, nada en bruno/catalogs/ probaria que
-- GET /api/catalogs/currencies filtra por is_active. XTS es el codigo que ISO 4217 reserva para
-- pruebas, y va aqui y no en seed.sql porque en Neon no debe existir.
INSERT INTO finance.currencies (code, name, symbol, decimal_places, is_active)
VALUES ('XTS', 'Moneda de prueba', 'XTS', 2, FALSE)
ON CONFLICT (code) DO UPDATE SET is_active = FALSE;


-- -----------------------------------------------------------------------------
-- Instante del mes en curso: el desfase desde el día 1 en hora de Bogotá,
-- recortado al pasado. Un movimiento con fecha futura estaría programado
-- (FA-106) y no contaría, así que sin el recorte los totales del escenario
-- dependerían del día del mes en que se cargue. El recorte deja cada fila
-- distinta, antes de now() y en el mismo orden del desfase: la que caería en el
-- futuro queda a (31 días - desfase) / 1000 antes de now(), menos de 45 minutos.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.en_el_mes(desfase INTERVAL)
RETURNS TIMESTAMPTZ
LANGUAGE sql
STABLE
AS $$
    SELECT LEAST(date_trunc('month', now() AT TIME ZONE 'America/Bogota') + desfase,
                 (now() AT TIME ZONE 'America/Bogota') - (INTERVAL '31 days' - desfase) / 1000)
           AT TIME ZONE 'America/Bogota';
$$;


-- -----------------------------------------------------------------------------
-- Gastos e ingresos del mes en curso
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, m.account_id, c.id, m.type, m.amount, 'COP',
       1, m.amount, m.description, pg_temp.en_el_mes(m.desfase), m.origin
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
        (:'cash'::uuid,   'Gimnasio',        'EXPENSE',  120000::numeric, 'Mensualidad',         INTERVAL '25 day 19 hours', 'WEB'),
        (:'cash'::uuid,   'Restaurantes',    'EXPENSE',   50000::numeric, 'Cena',                INTERVAL '27 day 21 hours 30 minutes', 'TELEGRAM')
       ) AS m(account_id, category_name, type, amount, description, desfase, origin)
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = m.category_name;


-- -----------------------------------------------------------------------------
-- Transferencias del mes en curso
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, destination_amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'debit'::uuid, :'cash'::uuid, 'TRANSFER',
       300000, NULL, 'COP', 1, 300000, 'Retiro de cajero',
       pg_temp.en_el_mes(INTERVAL '2 day 12 hours'), 'WEB';

-- Transferencia entre monedas distintas: salen 100 USD y entran 410.000 COP.
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, destination_amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'usd'::uuid, :'debit'::uuid, 'TRANSFER',
       100, 410000, 'USD', 4100, 410000, 'Cambio de dólares',
       pg_temp.en_el_mes(INTERVAL '9 day 15 hours'), 'WEB';


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
--
-- Los buses caen el último día del mes a las 21:30 de Bogotá, que en UTC ya es
-- el día siguiente: prueban la frontera del día. Van en este mes y no en el en
-- curso porque una fila a fin del mes en curso estaría programada (FA-106).
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
        (:'credit'::uuid, 'Transporte','EXPENSE',   35000::numeric, 'Buses de fin de mes', INTERVAL '1 month' - INTERVAL '2 hours 30 minutes')
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


-- -----------------------------------------------------------------------------
-- Movimientos pendientes de aprobación (FA-76)
--
-- Usuario aparte, con la misma clave, para bruno/pending/: aprobar mueve saldos
-- y totales, y en prueba@ los verifican otras carpetas. Esa carpeta aprueba y
-- rechaza estas filas, así que entre dos corridas hay que recargar este script.
--
-- Saldos con solo el gasto confirmado: Cuenta pendientes 970.000, Bolsillo 0.
-- Los pendientes no los mueven hasta aprobarse.
-- -----------------------------------------------------------------------------
INSERT INTO finance.users
    (id, email, password_hash, first_name, base_currency_code, timezone)
VALUES
    (:'pendientes'::uuid, 'pendientes@financeapp.local',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Pendientes', 'COP', 'America/Bogota');

INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), :'pendientes'::uuid, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 WHERE d.is_active;

INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance)
VALUES
    (:'cuenta_pendientes'::uuid,   :'pendientes'::uuid, 'Cuenta pendientes',   'DEBIT', 'COP', 1000000),
    (:'bolsillo_pendientes'::uuid, :'pendientes'::uuid, 'Bolsillo pendientes', 'CASH',  'COP',       0);

-- Del más antiguo al más reciente: el confirmado, el pendiente que se rechaza, la
-- transferencia y el gasto. GET /api/transactions/pending los devuelve al revés.
-- Los pendientes llevan origen TELEGRAM, como los que crea el asistente (FA-77).
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, category_id, type, amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, status, origin)
SELECT m.id, :'pendientes'::uuid, :'cuenta_pendientes'::uuid, m.destino,
       (SELECT c.id FROM finance.categories c
         WHERE c.user_id = :'pendientes'::uuid AND c.name = m.category_name),
       m.type, m.amount, 'COP', 1, m.amount, m.description,
       pg_temp.en_el_mes(m.desfase), m.status,
       CASE m.status WHEN 'PENDING' THEN 'TELEGRAM' ELSE 'WEB' END
  FROM (VALUES
        (gen_random_uuid(),           NULL::uuid,                     'Mercado', 'EXPENSE',   30000::numeric, 'Mercado confirmado',  INTERVAL '0 day 10 hours', 'CONFIRMED'),
        (:'pendiente_rechazo'::uuid,  NULL::uuid,                     'Mercado', 'EXPENSE',   20000::numeric, 'Gasto mal leído',     INTERVAL '0 day 11 hours', 'PENDING'),
        (:'pendiente_traslado'::uuid, :'bolsillo_pendientes'::uuid,   NULL,      'TRANSFER', 100000::numeric, 'Al bolsillo',         INTERVAL '0 day 12 hours', 'PENDING'),
        (:'pendiente_gasto'::uuid,    NULL::uuid,                     'Mercado', 'EXPENSE',   45000::numeric, 'Mercado del asistente', INTERVAL '0 day 13 hours', 'PENDING')
       ) AS m(id, destino, category_name, type, amount, description, desfase, status);


-- -----------------------------------------------------------------------------
-- Series recurrentes (FA-107)
--
-- Usuario aparte para bruno/recurrences/, con la misma clave: crea, edita y
-- cancela series, y eso mueve saldos que otras carpetas verifican en prueba@.
-- Entre dos corridas hay que recargar este script.
--
-- La serie atrasada es semanal y sin fin, del mismo día de la semana que hoy, y
-- empezó hace cuatro semanas. Su contador dice que ya creó dos ocurrencias, pero
-- solo existe la primera: la segunda la "borró a mano" el usuario. La primera
-- lectura de saldos tiene que crear las de hace dos semanas, hace una y hoy, más
-- la de dentro de una semana, y no volver a crear la borrada.
--
-- Saldos al cargar: Serie atrasada 990.000 (solo la primera ocurrencia),
-- Series 2.000.000 y Series otra 0.
-- -----------------------------------------------------------------------------
INSERT INTO finance.users
    (id, email, password_hash, first_name, base_currency_code, timezone)
VALUES
    (:'series'::uuid, 'series@financeapp.local',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Series', 'COP', 'America/Bogota');

INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), :'series'::uuid, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 WHERE d.is_active;

INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance)
VALUES
    (:'cuenta_atrasada'::uuid,    :'series'::uuid, 'Serie atrasada', 'CASH', 'COP', 1000000),
    (:'cuenta_series'::uuid,      :'series'::uuid, 'Series',         'CASH', 'COP', 2000000),
    (:'cuenta_series_otra'::uuid, :'series'::uuid, 'Series otra',    'CASH', 'COP',       0);

INSERT INTO finance.recurrences
    (id, user_id, account_id, category_id, type, amount, currency_code, description,
     frequency, interval_count, day_of_week, start_date, generated_count)
SELECT :'serie_atrasada'::uuid, :'series'::uuid, :'cuenta_atrasada'::uuid, c.id, 'EXPENSE', 10000, 'COP',
       'Gimnasio semanal', 'WEEKLY', 1, EXTRACT(ISODOW FROM r.inicio), r.inicio, 2
  FROM (SELECT (now() AT TIME ZONE 'America/Bogota')::date - 28 AS inicio) r
  JOIN finance.categories c
    ON c.user_id = :'series'::uuid AND c.name = 'Suscripciones';

INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin, recurrence_id)
SELECT gen_random_uuid(), :'series'::uuid, s.account_id, s.category_id, s.type, s.amount, 'COP',
       1, s.amount, s.description, s.start_date::timestamp AT TIME ZONE 'America/Bogota', 'WEB', s.id
  FROM finance.recurrences s
 WHERE s.id = :'serie_atrasada'::uuid;


-- -----------------------------------------------------------------------------
-- Compras en cuotas (FA-108)
--
-- Usuario aparte para bruno/installments/, con la misma clave: registra,
-- edita y cancela compras, y eso mueve saldos y cupos. Entre dos corridas hay
-- que recargar este script. Las cuatro cuentas empiezan en 0:
--   Visa            cupo 5.000.000, corte 20, pago 5, tasa 2
--   Visa 31         cupo 2.000.000, corte 31, pago 31, sin tasa
--   Visa sin corte  cupo 1.000.000, sin corte, pago 5
--   Ahorros         SAVINGS
--
-- La compra ajena es de prueba@, sobre su tarjeta y sin cuotas: solo existe
-- para que la edición y la cancelación de cuotas@ den 404. Sin cuotas no mueve
-- el saldo ni el cupo que verifican las demás carpetas.
-- -----------------------------------------------------------------------------
INSERT INTO finance.users
    (id, email, password_hash, first_name, base_currency_code, timezone)
VALUES
    (:'cuotas'::uuid, 'cuotas@financeapp.local',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Cuotas', 'COP', 'America/Bogota');

INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), :'cuotas'::uuid, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 WHERE d.is_active;

INSERT INTO finance.accounts
    (id, user_id, name, type, currency_code, initial_balance,
     credit_limit, statement_day, payment_due_day, monthly_interest_rate)
VALUES
    (:'visa'::uuid,           :'cuotas'::uuid, 'Visa',           'CREDIT',  'COP', 0, 5000000,   20,    5,    2),
    (:'visa_31'::uuid,        :'cuotas'::uuid, 'Visa 31',        'CREDIT',  'COP', 0, 2000000,   31,   31, NULL),
    (:'visa_sin_corte'::uuid, :'cuotas'::uuid, 'Visa sin corte', 'CREDIT',  'COP', 0, 1000000, NULL,    5, NULL),
    (:'ahorros_cuotas'::uuid, :'cuotas'::uuid, 'Ahorros',        'SAVINGS', 'COP', 0,    NULL, NULL, NULL, NULL);

INSERT INTO finance.installment_purchases
    (id, user_id, account_id, category_id, amount, currency_code, description,
     purchase_date, installment_count, monthly_interest_rate)
SELECT :'compra_ajena'::uuid, :'uid'::uuid, :'credit'::uuid, c.id, 600000, 'COP', 'Compra ajena',
       (now() AT TIME ZONE 'America/Bogota')::date, 3, 0
  FROM finance.categories c
 WHERE c.user_id = :'uid'::uuid AND c.applies_to = 'EXPENSE' AND c.deleted_at IS NULL
 ORDER BY c.name
 LIMIT 1;

COMMIT;

\echo ''
\echo 'Escenario de pruebas recreado para prueba@financeapp.local'
SELECT (SELECT count(*) FROM finance.accounts     WHERE user_id = :'uid'::uuid) AS cuentas,
       (SELECT count(*) FROM finance.categories   WHERE user_id = :'uid'::uuid) AS categorias,
       (SELECT count(*) FROM finance.transactions WHERE user_id = :'uid'::uuid) AS movimientos,
       (SELECT count(*) FROM finance.budgets      WHERE user_id = :'uid'::uuid) AS metas,
       (SELECT count(*) FROM finance.transactions WHERE user_id = :'pendientes'::uuid
                                                    AND status = 'PENDING') AS pendientes;
