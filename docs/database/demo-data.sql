-- =============================================================================
-- financeapp-bk : datos de demo
--
-- Crea un usuario con seis meses de movimientos, para la demo, las pruebas del
-- front y las del desarrollador. No es el escenario de bruno/: ese es
-- test-data.sql, con totales que las specs fijan. Aqui los montos se pueden
-- cambiar cuando el front lo necesite sin romper nada.
--
-- Recibe dos variables de psql:
--   email  correo del usuario     (por defecto demo@financeapp.local)
--   u      un digito para los ids (por defecto 0): usuario 4000000<u>-...,
--          cuentas 5000000<u>-..., categorias propias 6000000<u>-...
-- Corrido solo carga la demo del front. cargar-datos-local.sql lo incluye
-- tambien para dev@financeapp.local con u = 1. En una sesion de psql que ya
-- tenga esas variables, carga el usuario que digan ellas.
--
-- Front, back y desarrollador comparten la base local, separados por usuario:
--   back : prueba@ e inactivo@financeapp.local, y los *@bruno.local  -> test-data.sql
--   front: demo@financeapp.local, y los *@front.local                -> este script
--   dev  : dev@financeapp.local                                      -> este script
-- Los usuarios que el front registre desde la interfaz van con correo
-- @front.local: recargar la demo los borra.
--
-- Re-ejecutable: borra el usuario con todo lo suyo y lo recrea. Las fechas son
-- relativas al mes en curso, y del mes en curso solo entran los movimientos que
-- ya ocurrieron: dos cargas el mismo dia dejan el mismo estado.
--
-- Solo para la base local; en Neon no se carga.
--
-- Ejecutar:  psql -U postgres -d financeapp -f docs/database/demo-data.sql
-- =============================================================================

\set ON_ERROR_STOP on
SET search_path TO finance, public;

\if :{?email}
\else
\set email 'demo@financeapp.local'
\endif
\if :{?u}
\else
\set u 0
\endif

\set uid        '4000000' :u '-0000-7000-8000-000000000001'
\set efectivo   '5000000' :u '-0000-7000-8000-000000000001'
\set nomina     '5000000' :u '-0000-7000-8000-000000000002'
\set tarjeta    '5000000' :u '-0000-7000-8000-000000000003'
\set ahorros    '5000000' :u '-0000-7000-8000-000000000004'
\set cdt        '5000000' :u '-0000-7000-8000-000000000005'
\set nequi      '5000000' :u '-0000-7000-8000-000000000006'
\set dolares    '5000000' :u '-0000-7000-8000-000000000007'
\set gimnasio   '6000000' :u '-0000-7000-8000-000000000001'
\set jardin     '6000000' :u '-0000-7000-8000-000000000002'

BEGIN;

-- Cuentas, categorias, movimientos y metas caen en cascada. Por id y por correo,
-- por si alguien le cambio el correo al usuario desde el API. Los @front.local
-- solo se limpian al recargar la demo del front.
DELETE FROM finance.users
 WHERE id = :'uid'::uuid
    OR email = :'email'
    OR (:'email' = 'demo@financeapp.local' AND email LIKE '%@front.local');

-- Los usuarios que el front registra desde la interfaz necesitan su dominio en la
-- lista de admitidos del alta (FA-103). La lista es compartida con test-data.sql
-- y este script corre una vez por usuario, por eso ON CONFLICT.
INSERT INTO finance.registration_allowlist (entry)
VALUES ('@front.local')
ON CONFLICT DO NOTHING;


-- -----------------------------------------------------------------------------
-- Usuario
-- -----------------------------------------------------------------------------
-- Contrasena 'claveDePrueba123', el mismo hash BCrypt de test-data.sql: psql no
-- calcula BCrypt sin pgcrypto. Dato de la base local, no una credencial.
INSERT INTO finance.users
    (id, email, password_hash, first_name, last_name, phone, birth_date,
     base_currency_code, timezone)
VALUES
    (:'uid'::uuid, :'email',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy',
     'Laura', 'Gómez', '3109876543', DATE '1992-07-21',
     'COP', 'America/Bogota');


-- -----------------------------------------------------------------------------
-- Categorias: copia de la semilla, como hace el registro, y dos propias
-- -----------------------------------------------------------------------------
INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), :'uid'::uuid, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 WHERE d.is_active;

-- Jardin no tiene ningun movimiento: es el caso de la categoria vacia.
INSERT INTO finance.categories (id, user_id, name, applies_to, icon, color, sort_order)
VALUES
    (:'gimnasio'::uuid, :'uid'::uuid, 'Gimnasio', 'EXPENSE', 'dumbbell', '#00695C', 300),
    (:'jardin'::uuid,   :'uid'::uuid, 'Jardín',   'EXPENSE', 'flower',   '#558B2F', 310);


-- -----------------------------------------------------------------------------
-- Cuentas
--
-- Nequi (saldo cero) y la cuenta en dolares no tienen movimientos: el API solo
-- acepta movimientos en COP.
-- -----------------------------------------------------------------------------
INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance)
VALUES
    (:'efectivo'::uuid, :'uid'::uuid, 'Efectivo',           'CASH',       'COP',   350000),
    (:'nomina'::uuid,   :'uid'::uuid, 'Bancolombia Nómina', 'DEBIT',      'COP',  3500000),
    (:'ahorros'::uuid,  :'uid'::uuid, 'Ahorros Davivienda', 'SAVINGS',    'COP',  6000000),
    (:'cdt'::uuid,      :'uid'::uuid, 'CDT Bancolombia',    'INVESTMENT', 'COP', 10000000),
    (:'nequi'::uuid,    :'uid'::uuid, 'Nequi',              'DEBIT',      'COP',        0),
    (:'dolares'::uuid,  :'uid'::uuid, 'Cuenta en dólares',  'SAVINGS',    'USD',     1500.50);

INSERT INTO finance.accounts
    (id, user_id, name, type, currency_code, initial_balance,
     credit_limit, statement_day, payment_due_day, monthly_interest_rate)
VALUES
    (:'tarjeta'::uuid, :'uid'::uuid, 'Mastercard Oro', 'CREDIT', 'COP', 0, 8000000, 20, 5, 1.89);


-- -----------------------------------------------------------------------------
-- Gastos e ingresos
--
-- mes = meses hacia atras desde el mes en curso (0 = este mes). La fecha es el
-- primer dia de ese mes en hora de Bogota mas el desfase. Los fijos se repiten
-- en todos los meses menos el 3, que es el mes con pocos movimientos; su monto
-- cambia un poco cada mes con la columna variacion.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, x.account_id, c.id, x.type, x.amount, 'COP',
       1, x.amount, x.description, t.occurred_at, x.origin
  FROM (
        SELECT f.account_id, f.category_name, f.type, f.amount + f.variacion * meses.mes AS amount,
               f.description, f.desfase, f.origin, meses.mes
          FROM (VALUES
                (:'nomina'::uuid,  'Salario',       'INCOME',  2600000::numeric,      0::numeric, 'Salario quincena 1', INTERVAL '0 day 9 hours',   'WEB'),
                (:'nomina'::uuid,  'Vivienda',      'EXPENSE', 1650000::numeric,      0::numeric, 'Arriendo',           INTERVAL '1 day 8 hours',   'WEB'),
                (:'tarjeta'::uuid, 'Gimnasio',      'EXPENSE',  129000::numeric,      0::numeric, 'Mensualidad gimnasio', INTERVAL '2 day 18 hours', 'WEB'),
                (:'nomina'::uuid,  'Servicios',     'EXPENSE',  149900::numeric,      0::numeric, 'Internet y celular', INTERVAL '4 day 10 hours',  'IMPORT'),
                (:'tarjeta'::uuid, 'Suscripciones', 'EXPENSE',   44900::numeric,      0::numeric, 'Streaming',          INTERVAL '5 day 7 hours',   'IMPORT'),
                (:'tarjeta'::uuid, 'Suscripciones', 'EXPENSE',   16900::numeric,      0::numeric, 'Música',             INTERVAL '5 day 8 hours',   'IMPORT'),
                (:'nomina'::uuid,  'Servicios',     'EXPENSE',  182430.75::numeric, 4120.50::numeric, 'Luz, agua y gas', INTERVAL '8 day 11 hours', 'WEB'),
                (:'nomina'::uuid,  'Salario',       'INCOME',  2600000::numeric,      0::numeric, 'Salario quincena 2', INTERVAL '14 day 9 hours',  'WEB')
               ) AS f(account_id, category_name, type, amount, variacion, description, desfase, origin)
         CROSS JOIN (VALUES (0), (1), (2), (4), (5)) AS meses(mes)

        UNION ALL

        SELECT v.account_id, v.category_name, v.type, v.amount, v.description, v.desfase, v.origin, v.mes
          FROM (VALUES
                -- Mes en curso
                (0, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  187650.40::numeric, 'Mercado semanal',          INTERVAL '2 day 11 hours',  'WEB'),
                (0, :'efectivo'::uuid, 'Transporte',      'EXPENSE',   18500::numeric,    'Taxi',                     INTERVAL '2 day 22 hours',  'TELEGRAM'),
                (0, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',   64300::numeric,    'Almuerzo con el equipo',   INTERVAL '4 day 13 hours',  'TELEGRAM'),
                (0, :'efectivo'::uuid, 'Mercado',         'EXPENSE',   32750.50::numeric, 'Fruta y verdura',          INTERVAL '5 day 17 hours',  'TELEGRAM'),
                (0, :'tarjeta'::uuid,  'Entretenimiento', 'EXPENSE',   58000::numeric,    'Cine',                     INTERVAL '6 day 20 hours',  'WEB'),
                (0, :'nomina'::uuid,   'Salud',           'EXPENSE',   47890.25::numeric, 'Farmacia',                 INTERVAL '7 day 10 hours',  'WEB'),
                (0, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  201340::numeric,    'Mercado semanal',          INTERVAL '9 day 11 hours',  'WEB'),
                (0, :'tarjeta'::uuid,  'Transporte',      'EXPENSE',  120000::numeric,    'Gasolina',                 INTERVAL '12 day 8 hours',  'WEB'),
                (0, :'nomina'::uuid,   'Freelance',       'INCOME',   950000::numeric,    'Diseño de logo',           INTERVAL '16 day 15 hours', 'WEB'),
                (0, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',  236500::numeric,    'Cena de cumpleaños',       INTERVAL '18 day 21 hours', 'WEB'),
                (0, :'efectivo'::uuid, 'Transporte',      'EXPENSE',    9800::numeric,    'Bus',                      INTERVAL '20 day 7 hours',  'TELEGRAM'),
                (0, :'tarjeta'::uuid,  'Ropa',            'EXPENSE',  289900::numeric,    'Zapatos',                  INTERVAL '23 day 16 hours', 'WEB'),
                (0, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  176420.80::numeric, 'Mercado semanal',          INTERVAL '24 day 11 hours', 'WEB'),

                -- Hace un mes
                (1, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  195300.60::numeric, 'Mercado semanal',          INTERVAL '1 day 11 hours',  'WEB'),
                (1, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',   38500::numeric,    'Almuerzo',                 INTERVAL '3 day 13 hours',  'TELEGRAM'),
                (1, :'efectivo'::uuid, 'Transporte',      'EXPENSE',   22000::numeric,    'Taxi',                     INTERVAL '6 day 23 hours',  'TELEGRAM'),
                (1, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  182760.30::numeric, 'Mercado semanal',          INTERVAL '8 day 11 hours',  'WEB'),
                (1, :'nomina'::uuid,   'Salud',           'EXPENSE',  180000::numeric,    'Consulta odontológica',    INTERVAL '10 day 9 hours',  'WEB'),
                (1, :'tarjeta'::uuid,  'Entretenimiento', 'EXPENSE',  320000::numeric,    'Concierto',                INTERVAL '12 day 20 hours', 'WEB'),
                (1, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  210450::numeric,    'Mercado semanal',          INTERVAL '15 day 11 hours', 'WEB'),
                (1, :'nomina'::uuid,   'Freelance',       'INCOME',  2800000::numeric,    'Proyecto web',             INTERVAL '17 day 16 hours', 'WEB'),
                (1, :'tarjeta'::uuid,  'Regalos',         'EXPENSE',  150000::numeric,    'Regalo de cumpleaños',     INTERVAL '19 day 18 hours', 'WEB'),
                (1, :'tarjeta'::uuid,  'Transporte',      'EXPENSE',  135000::numeric,    'Gasolina',                 INTERVAL '21 day 8 hours',  'WEB'),
                (1, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  198230.45::numeric, 'Mercado semanal',          INTERVAL '22 day 11 hours', 'WEB'),
                (1, :'nomina'::uuid,   'Educación',       'EXPENSE',  349000::numeric,    'Curso de inglés',          INTERVAL '24 day 19 hours', 'IMPORT'),
                (1, :'efectivo'::uuid, 'Restaurantes',    'EXPENSE',   15600::numeric,    'Empanadas',                INTERVAL '27 day 17 hours', 'TELEGRAM'),

                -- Hace dos meses
                (2, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  176980.25::numeric, 'Mercado semanal',          INTERVAL '2 day 11 hours',  'WEB'),
                (2, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',   42000::numeric,    'Almuerzo',                 INTERVAL '4 day 13 hours',  'TELEGRAM'),
                (2, :'tarjeta'::uuid,  'Tecnología',      'EXPENSE',  459900::numeric,    'Audífonos',                INTERVAL '6 day 15 hours',  'WEB'),
                (2, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  205640::numeric,    'Mercado semanal',          INTERVAL '9 day 11 hours',  'WEB'),
                (2, :'efectivo'::uuid, 'Transporte',      'EXPENSE',   26700::numeric,    'Taxi',                     INTERVAL '11 day 22 hours', 'TELEGRAM'),
                (2, :'cdt'::uuid,      'Intereses',       'INCOME',    83456.78::numeric, 'Intereses CDT',            INTERVAL '14 day 6 hours',  'IMPORT'),
                (2, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  189320.70::numeric, 'Mercado semanal',          INTERVAL '16 day 11 hours', 'WEB'),
                (2, :'tarjeta'::uuid,  'Mascotas',        'EXPENSE',  135000::numeric,    'Veterinario',              INTERVAL '18 day 10 hours', 'WEB'),
                (2, :'tarjeta'::uuid,  'Entretenimiento', 'EXPENSE',  219000::numeric,    'Videojuego',               INTERVAL '20 day 21 hours', 'WEB'),
                (2, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  201110::numeric,    'Mercado semanal',          INTERVAL '23 day 11 hours', 'WEB'),
                (2, :'nomina'::uuid,   'Impuestos',       'EXPENSE',  612000::numeric,    'Impuesto vehicular',       INTERVAL '25 day 10 hours', 'WEB'),
                (2, :'efectivo'::uuid, 'Restaurantes',    'EXPENSE',    9500.50::numeric, 'Café',                     INTERVAL '27 day 8 hours',  'TELEGRAM'),

                -- Hace tres meses: el mes con pocos movimientos, sin los fijos
                (3, :'nomina'::uuid,   'Salario',         'INCOME',  5200000::numeric,    'Salario del mes',          INTERVAL '0 day 9 hours',   'WEB'),
                (3, :'nomina'::uuid,   'Vivienda',        'EXPENSE', 1650000::numeric,    'Arriendo',                 INTERVAL '1 day 8 hours',   'WEB'),
                (3, :'tarjeta'::uuid,  'Viajes',          'EXPENSE', 1250000::numeric,    'Tiquetes a Cartagena',     INTERVAL '10 day 14 hours', 'WEB'),

                -- Hace cuatro meses
                (4, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  168790.35::numeric, 'Mercado semanal',          INTERVAL '1 day 11 hours',  'WEB'),
                (4, :'tarjeta'::uuid,  'Tecnología',      'EXPENSE', 4850000::numeric,    'Portátil',                 INTERVAL '3 day 16 hours',  'WEB'),
                (4, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',   35000::numeric,    'Almuerzo',                 INTERVAL '5 day 13 hours',  'TELEGRAM'),
                (4, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  192450::numeric,    'Mercado semanal',          INTERVAL '8 day 11 hours',  'WEB'),
                (4, :'efectivo'::uuid, 'Transporte',      'EXPENSE',   19900::numeric,    'Taxi',                     INTERVAL '10 day 23 hours', 'TELEGRAM'),
                (4, :'nomina'::uuid,   'Ventas',          'INCOME',  1200000::numeric,    'Venta del portátil viejo', INTERVAL '12 day 17 hours', 'WEB'),
                (4, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  181230.15::numeric, 'Mercado semanal',          INTERVAL '15 day 11 hours', 'WEB'),
                (4, :'nomina'::uuid,   'Salud',           'EXPENSE',   98750.50::numeric, 'Exámenes de laboratorio',  INTERVAL '17 day 7 hours',  'WEB'),
                (4, :'tarjeta'::uuid,  'Ropa',            'EXPENSE',  239900::numeric,    'Chaqueta',                 INTERVAL '19 day 15 hours', 'WEB'),
                (4, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  199870::numeric,    'Mercado semanal',          INTERVAL '22 day 11 hours', 'WEB'),
                (4, :'efectivo'::uuid, 'Otros gastos',    'EXPENSE',   10000::numeric,    'Propina',                  INTERVAL '25 day 20 hours', 'TELEGRAM'),

                -- Hace cinco meses
                (5, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  172340.90::numeric, 'Mercado semanal',          INTERVAL '2 day 11 hours',  'WEB'),
                (5, :'tarjeta'::uuid,  'Restaurantes',    'EXPENSE',   41000::numeric,    'Almuerzo',                 INTERVAL '4 day 13 hours',  'TELEGRAM'),
                (5, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  185600::numeric,    'Mercado semanal',          INTERVAL '9 day 11 hours',  'WEB'),
                (5, :'efectivo'::uuid, 'Transporte',      'EXPENSE',   21300::numeric,    'Taxi',                     INTERVAL '12 day 22 hours', 'TELEGRAM'),
                (5, :'nomina'::uuid,   'Reembolsos',      'INCOME',    87500.25::numeric, 'Reembolso EPS',            INTERVAL '14 day 10 hours', 'WEB'),
                (5, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  194210::numeric,    'Mercado semanal',          INTERVAL '16 day 11 hours', 'WEB'),
                (5, :'nomina'::uuid,   'Otros ingresos',  'INCOME',  2600000::numeric,    'Prima de servicios',       INTERVAL '19 day 9 hours',  'WEB'),
                (5, :'tarjeta'::uuid,  'Entretenimiento', 'EXPENSE',  140000::numeric,    'Teatro',                   INTERVAL '20 day 19 hours', 'WEB'),
                (5, :'tarjeta'::uuid,  'Mercado',         'EXPENSE',  203980.60::numeric, 'Mercado semanal',          INTERVAL '23 day 11 hours', 'WEB'),
                (5, :'nomina'::uuid,   'Educación',       'EXPENSE',  186000::numeric,    'Libros',                   INTERVAL '26 day 16 hours', 'WEB')
               ) AS v(mes, account_id, category_name, type, amount, description, desfase, origin)
       ) AS x(account_id, category_name, type, amount, description, desfase, origin, mes)
 CROSS JOIN LATERAL (
        SELECT (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                - make_interval(months => x.mes) + x.desfase) AT TIME ZONE 'America/Bogota' AS occurred_at
       ) AS t
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = x.category_name
 WHERE t.occurred_at <= now();


-- -----------------------------------------------------------------------------
-- Transferencias fijas de cada mes, menos el 3
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, f.account_id, f.destination_id, 'TRANSFER',
       f.amount, 'COP', 1, f.amount, f.description, t.occurred_at, 'WEB'
  FROM (VALUES
        (:'nomina'::uuid, :'efectivo'::uuid,  300000::numeric, 'Retiro de cajero',        INTERVAL '3 day 12 hours'),
        (:'nomina'::uuid, :'tarjeta'::uuid,  1600000::numeric, 'Pago tarjeta Mastercard', INTERVAL '4 day 9 hours'),
        (:'nomina'::uuid, :'ahorros'::uuid,   500000::numeric, 'Ahorro del mes',          INTERVAL '15 day 11 hours')
       ) AS f(account_id, destination_id, amount, description, desfase)
 CROSS JOIN (VALUES (0), (1), (2), (4), (5)) AS meses(mes)
 CROSS JOIN LATERAL (
        SELECT (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                - make_interval(months => meses.mes) + f.desfase) AT TIME ZONE 'America/Bogota' AS occurred_at
       ) AS t
 WHERE t.occurred_at <= now();


-- -----------------------------------------------------------------------------
-- Metas
--   * Globales en todos los meses menos el 3, que queda sin meta
--   * Hace cuatro meses el portatil pasa la meta global
--   * Hace un mes Mercado pasa su tope (786.741,35 sobre 700.000)
-- -----------------------------------------------------------------------------
INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code)
SELECT gen_random_uuid(), :'uid'::uuid,
       (date_trunc('month', now() AT TIME ZONE 'America/Bogota') - make_interval(months => b.mes))::date,
       NULL, b.amount, 'COP'
  FROM (VALUES (0, 4500000::numeric), (1, 4500000::numeric), (2, 4200000::numeric),
               (4, 4000000::numeric), (5, 4000000::numeric)) AS b(mes, amount);

INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code)
SELECT gen_random_uuid(), :'uid'::uuid,
       (date_trunc('month', now() AT TIME ZONE 'America/Bogota') - make_interval(months => b.mes))::date,
       c.id, b.amount, 'COP'
  FROM (VALUES
        (0, 'Mercado',          800000::numeric),
        (0, 'Restaurantes',     300000::numeric),
        (0, 'Transporte',       250000::numeric),
        (0, 'Entretenimiento',  200000::numeric),
        (1, 'Mercado',          700000::numeric),
        (1, 'Restaurantes',     150000::numeric)
       ) AS b(mes, category_name, amount)
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = b.category_name;

COMMIT;

\echo ''
\echo 'Datos de demo recreados para' :email '(clave claveDePrueba123)'
SELECT to_char(occurred_at AT TIME ZONE 'America/Bogota', 'YYYY-MM') AS mes,
       count(*) FILTER (WHERE type = 'EXPENSE')  AS gastos,
       count(*) FILTER (WHERE type = 'INCOME')   AS ingresos,
       count(*) FILTER (WHERE type = 'TRANSFER') AS transferencias
  FROM finance.transactions
 WHERE user_id = :'uid'::uuid
 GROUP BY 1
 ORDER BY 1;
SELECT name AS cuenta, type AS tipo, currency_code AS moneda, current_balance AS saldo
  FROM finance.accounts
 WHERE user_id = :'uid'::uuid
 ORDER BY name;
