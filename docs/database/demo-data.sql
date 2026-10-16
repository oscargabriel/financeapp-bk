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
-- Junto al usuario crea tres personas con la misma clave (FIN-30), en
-- <nombre>@<local del usuario>.financeapp.local: carlos@, valentina@ y andres@
-- demo.financeapp.local para demo@, y las mismas en dev.financeapp.local para
-- dev@. Carlos y Valentina estan afiliados al usuario y comparten gastos con el
-- en los dos sentidos; Andres le envio una solicitud sin responder, y el usuario
-- tiene otra abierta a sofia@, que no tiene cuenta. Ningun correo de esos es de
-- alguien real ni entra en la lista de admitidos del alta.
--
-- Ademas de los pendientes que le dejan esas personas, el usuario tiene dos
-- grupos pendientes del asistente (FIN-95), como los deja un mensaje a Telegram:
-- uno de tres movimientos y otro de uno, para la pantalla de pendientes del
-- front y su filtro por persona, que los deja fuera (FIN-71, FIN-73).
--
-- Los casos en dolares (FIN-96): la cuenta en dolares con ingresos, gastos y una
-- serie mensual; una tarjeta en USD con una compra en cuotas; transferencias
-- entre la nomina y la cuenta en dolares en los dos sentidos, con
-- destination_amount; y un gasto que llego en USD a la tarjeta en COP, ya
-- aprobado, con original_amount. Los montos convertidos salen de la tasa USD->COP
-- de la base para la fecha de cada movimiento, no de un valor fijo.
--
-- Front, back y desarrollador comparten la base local, separados por usuario:
--   back : prueba@ e inactivo@financeapp.local, y los *@bruno.local  -> test-data.sql
--   front: demo@financeapp.local, los *@demo.financeapp.local y los
--          *@front.local                                             -> este script
--   dev  : dev@financeapp.local y los *@dev.financeapp.local         -> este script
-- Los usuarios que el front registre desde la interfaz van con correo
-- @front.local: recargar la demo los borra.
--
-- Re-ejecutable: borra el usuario y sus tres personas con todo lo suyo, incluido
-- lo que se haya aprobado o rechazado desde el front, y los recrea: los
-- pendientes vuelven a quedar sin confirmar. Las fechas
-- son relativas al mes en curso, y del mes en curso solo entran los movimientos
-- que ya ocurrieron: dos cargas el mismo dia dejan el mismo estado. Lo
-- compartido va por dias hacia atras desde la carga.
--
-- Carga sobre schema.sql + seed.sql sin pasos previos. Los movimientos necesitan
-- una tasa USD->COP: si el par no tiene ninguna, el script inserta una de
-- respaldo, 4000 MANUAL, fechada el dia 1 de hace seis meses (FIN-45). Si ya hay
-- alguna (las de test-data.sql en local, las reales en Neon) no toca la tabla.
--
-- En Neon se carga solo para demo@ (FIN-43), con sus tres personas, y es seguro
-- correrlo ahi: borra y recrea solo a demo@ y a ellas, y no cambia las tasas
-- que ya existan. Cómo, en docs/despliegue.md.
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
\set visa_usd   '5000000' :u '-0000-7000-8000-000000000008'
\set gimnasio   '6000000' :u '-0000-7000-8000-000000000001'
\set jardin     '6000000' :u '-0000-7000-8000-000000000002'
\set club       '9000000' :u '-0000-7000-8000-000000000001'
\set nube       '9000000' :u '-0000-7000-8000-000000000002'
\set televisor  'a000000' :u '-0000-7000-8000-000000000001'
\set celular    'a000000' :u '-0000-7000-8000-000000000002'

-- Personas afiliadas (FIN-30): correos en <nombre>@<local del usuario>.financeapp.local,
-- para que demo@ y dev@ tengan las suyas.
SELECT split_part(:'email', '@', 1) || '.financeapp.local' AS dominio \gset
\set carlos           '4000000' :u '-0000-7000-8000-000000000002'
\set valentina        '4000000' :u '-0000-7000-8000-000000000003'
\set andres           '4000000' :u '-0000-7000-8000-000000000004'
\set carlos_email     'carlos@' :dominio
\set valentina_email  'valentina@' :dominio
\set andres_email     'andres@' :dominio
\set sofia_email      'sofia@' :dominio
\set cta_carlos       '5000000' :u '-0000-7000-8000-000000000011'
\set cta_valentina    '5000000' :u '-0000-7000-8000-000000000012'
\set cta_andres       '5000000' :u '-0000-7000-8000-000000000013'
\set af_carlos        'b000000' :u '-0000-7000-8000-000000000001'
\set af_valentina     'b000000' :u '-0000-7000-8000-000000000002'
\set af_andres        'b000000' :u '-0000-7000-8000-000000000003'
\set af_sofia         'b000000' :u '-0000-7000-8000-000000000004'
\set g1               'd000000' :u '-0000-7000-8000-000000000001'
\set g2               'd000000' :u '-0000-7000-8000-000000000002'
\set g3               'd000000' :u '-0000-7000-8000-000000000003'
\set g4               'd000000' :u '-0000-7000-8000-000000000004'
\set g5               'd000000' :u '-0000-7000-8000-000000000005'
\set g6               'd000000' :u '-0000-7000-8000-000000000006'
\set s1               'c000000' :u '-0000-7000-8000-000000000001'
\set s2               'c000000' :u '-0000-7000-8000-000000000002'
\set s3               'c000000' :u '-0000-7000-8000-000000000003'
\set s4               'c000000' :u '-0000-7000-8000-000000000004'
\set s5               'c000000' :u '-0000-7000-8000-000000000005'
\set s6               'c000000' :u '-0000-7000-8000-000000000006'
\set s7               'c000000' :u '-0000-7000-8000-000000000007'
\set s8               'c000000' :u '-0000-7000-8000-000000000008'
-- Grupos pendientes del asistente (FIN-95).
\set grupo_a          'e000000' :u '-0000-7000-8000-000000000001'
\set grupo_b          'e000000' :u '-0000-7000-8000-000000000002'

BEGIN;

-- Cuentas, categorias, movimientos y metas caen en cascada, y con las personas
-- sus afiliaciones y partes. Por id y por correo, por si alguien le cambio el
-- correo a un usuario desde el API. Los @front.local solo se limpian al recargar
-- la demo del front.
DELETE FROM finance.users
 WHERE id IN (:'uid'::uuid, :'carlos'::uuid, :'valentina'::uuid, :'andres'::uuid)
    OR email IN (:'email', :'carlos_email', :'valentina_email', :'andres_email')
    OR (:'email' = 'demo@financeapp.local' AND email LIKE '%@front.local');

-- Los usuarios que el front registra desde la interfaz necesitan su dominio en la
-- lista de admitidos del alta (FA-103). La lista es compartida con test-data.sql
-- y este script corre una vez por usuario, por eso ON CONFLICT.
INSERT INTO finance.registration_allowlist (entry)
VALUES ('@front.local')
ON CONFLICT DO NOTHING;

-- El trigger de transactions necesita alguna tasa USD->COP (FIN-45). Solo si el
-- par no tiene ninguna: las de test-data.sql o las reales de Neon no se tocan.
-- Fechada antes del movimiento mas antiguo, nunca hoy: una fila de hoy dejaria
-- al proveedor sin guardar la tasa real del dia.
INSERT INTO finance.exchange_rates (from_currency_code, to_currency_code, rate, rate_date, source)
SELECT 'USD', 'COP', 4000, (date_trunc('month', now() AT TIME ZONE 'America/Bogota') - INTERVAL '6 months')::date, 'MANUAL'
 WHERE NOT EXISTS (SELECT 1
                     FROM finance.exchange_rates
                    WHERE from_currency_code = 'USD'
                      AND to_currency_code = 'COP');


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
     'Laura', 'Gómez', '+573109876543', DATE '1992-07-21',
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
-- Cada cuenta en su moneda. La Visa en dolares existe para la compra en cuotas
-- en USD, que solo va sobre una tarjeta y queda en su moneda, y no tiene pagos.
-- Nequi queda en saldo cero, sin movimientos confirmados: solo la recarga
-- pendiente del asistente, hasta que se apruebe.
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
    (:'tarjeta'::uuid,  :'uid'::uuid, 'Mastercard Oro',  'CREDIT', 'COP', 0, 8000000, 20,  5, 1.89),
    (:'visa_usd'::uuid, :'uid'::uuid, 'Visa en dólares', 'CREDIT', 'USD', 0,    2000, 25, 10, 1.5);


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
-- Gastos e ingresos en dolares (FIN-96), en la cuenta en dolares y en su moneda,
-- con las mismas fechas por mes. La comision de manejo se repite en todos los
-- meses menos el 3, que solo tiene el hotel de los tiquetes a Cartagena.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, :'dolares'::uuid, c.id, x.type, x.amount, 'USD',
       1, x.amount, x.description, t.occurred_at, 'WEB'
  FROM (
        SELECT meses.mes, 'Otros gastos', 'EXPENSE', 3.50::numeric, 'Comisión de manejo', INTERVAL '0 day 7 hours'
          FROM (VALUES (0), (1), (2), (4), (5)) AS meses(mes)

        UNION ALL

        SELECT * FROM (VALUES
                (5, 'Freelance',       'INCOME',  650.00::numeric, 'Pago de cliente del exterior',          INTERVAL '5 day 10 hours'),
                (5, 'Educación',       'EXPENSE',  89.00::numeric, 'Curso en línea',                        INTERVAL '11 day 20 hours'),
                (4, 'Freelance',       'INCOME',  720.00::numeric, 'Pago de cliente del exterior',          INTERVAL '6 day 10 hours'),
                (4, 'Tecnología',      'EXPENSE',  59.99::numeric, 'Dominio y hosting',                     INTERVAL '13 day 9 hours'),
                (3, 'Viajes',          'EXPENSE', 145.30::numeric, 'Hotel en Cartagena reservado en línea', INTERVAL '10 day 15 hours'),
                (2, 'Freelance',       'INCOME',  650.00::numeric, 'Pago de cliente del exterior',          INTERVAL '5 day 10 hours'),
                (2, 'Ropa',            'EXPENSE',  72.45::numeric, 'Compra en tienda en línea',             INTERVAL '17 day 21 hours'),
                (1, 'Intereses',       'INCOME',    3.12::numeric, 'Intereses de la cuenta en dólares',     INTERVAL '27 day 6 hours'),
                (1, 'Entretenimiento', 'EXPENSE',  24.99::numeric, 'Videojuego en línea',                   INTERVAL '13 day 22 hours'),
                (0, 'Freelance',       'INCOME',  800.00::numeric, 'Pago de cliente del exterior',          INTERVAL '5 day 10 hours'),
                (0, 'Tecnología',      'EXPENSE',  34.50::numeric, 'Accesorios en línea',                   INTERVAL '11 day 19 hours')
               ) AS v
       ) AS x(mes, category_name, type, amount, description, desfase)
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
-- Transferencias entre monedas (FIN-96), con destination_amount como las manda
-- el front: lo convertido con la tasa de la base para la fecha, menos el 2 % que
-- se queda el banco, a los decimales del destino. En Neon sale de la tasa real.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, type, amount, destination_amount,
     currency_code, exchange_rate, amount_base, description, occurred_at, origin)
SELECT gen_random_uuid(), :'uid'::uuid, f.account_id, f.destination_id, 'TRANSFER', f.amount,
       round(finance.amount_in_currency(d.currency_code, o.currency_code, f.amount,
                                        (t.occurred_at AT TIME ZONE 'America/Bogota')::date) * 0.98,
             m.decimal_places),
       o.currency_code, 1, f.amount, f.description, t.occurred_at, 'WEB'
  FROM (VALUES
        (4, :'nomina'::uuid,  :'dolares'::uuid, 1200000::numeric, 'Compra de dólares', INTERVAL '7 day 11 hours'),
        (2, :'dolares'::uuid, :'nomina'::uuid,      300::numeric, 'Venta de dólares',  INTERVAL '20 day 12 hours'),
        (1, :'nomina'::uuid,  :'dolares'::uuid,  800000::numeric, 'Compra de dólares', INTERVAL '18 day 11 hours')
       ) AS f(mes, account_id, destination_id, amount, description, desfase)
  JOIN finance.accounts o ON o.id = f.account_id
  JOIN finance.accounts d ON d.id = f.destination_id
  JOIN finance.currencies m ON m.code = d.currency_code
 CROSS JOIN LATERAL (
        SELECT (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                - make_interval(months => f.mes) + f.desfase) AT TIME ZONE 'America/Bogota' AS occurred_at
       ) AS t
 WHERE t.occurred_at <= now();


-- -----------------------------------------------------------------------------
-- Series recurrentes (FA-107), sin fin y en la moneda de su cuenta: el club de
-- lectura el 10 de cada mes desde hace tres meses en la tarjeta, y el
-- almacenamiento en la nube el 12 desde hace cinco en la cuenta en dolares
-- (FIN-96). Quedan creadas las ocurrencias hasta hoy y la siguiente, programada,
-- como las deja el API. El contador dice cuántas creó.
-- -----------------------------------------------------------------------------
INSERT INTO finance.recurrences
    (id, user_id, account_id, category_id, type, amount, currency_code, description,
     frequency, interval_count, day_of_month, start_date, generated_count)
SELECT s.id, :'uid'::uuid, s.account_id, c.id, 'EXPENSE', s.amount, a.currency_code, s.description,
       'MONTHLY', 1, s.dia, d.inicio,
       (SELECT count(*) FROM generate_series(0, 12) k
         WHERE (d.inicio + make_interval(months => k))::date <= d.hoy) + 1
  FROM (VALUES
        (:'club'::uuid, :'tarjeta'::uuid, 35000::numeric, 'Club de lectura',            10, 3),
        (:'nube'::uuid, :'dolares'::uuid,  9.99::numeric, 'Almacenamiento en la nube', 12, 5)
       ) AS s(id, account_id, amount, description, dia, meses)
  JOIN finance.accounts a ON a.id = s.account_id
 CROSS JOIN LATERAL (
        SELECT (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                - make_interval(months => s.meses))::date + (s.dia - 1) AS inicio,
               (now() AT TIME ZONE 'America/Bogota')::date AS hoy
       ) AS d
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = 'Suscripciones';

INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, origin, recurrence_id)
SELECT gen_random_uuid(), s.user_id, s.account_id, s.category_id, s.type, s.amount, s.currency_code,
       1, s.amount, s.description,
       (s.start_date + make_interval(months => k)) AT TIME ZONE 'America/Bogota', 'WEB', s.id
  FROM finance.recurrences s
 CROSS JOIN generate_series(0, s.generated_count - 1) k
 WHERE s.id IN (:'club'::uuid, :'nube'::uuid);


-- -----------------------------------------------------------------------------
-- Compras en cuotas (FA-108), a 6 cuotas y en la moneda de su tarjeta:
--   * Televisor de 2.400.000 en la Mastercard, del 15 de hace dos meses. Con
--     corte 20 y pago 5, la cuota k vence el 5 del mes k despues de la compra.
--   * Celular de 600 USD en la Visa en dolares, del 5 de hace tres meses
--     (FIN-96). Con corte 25 y pago 10, la cuota k vence el 10 del mes k.
-- Capital fijo de amount / 6 mas la tasa de la tarjeta sobre el capital
-- pendiente, a los decimales de la moneda, como las calcula el API. Las
-- vencidas ya cuentan.
-- -----------------------------------------------------------------------------
INSERT INTO finance.installment_purchases
    (id, user_id, account_id, category_id, amount, currency_code, description,
     purchase_date, installment_count, monthly_interest_rate)
SELECT p.id, :'uid'::uuid, p.account_id, c.id, p.amount, a.currency_code, p.description,
       (date_trunc('month', now() AT TIME ZONE 'America/Bogota') - make_interval(months => p.meses))::date
           + (p.dia - 1),
       6, a.monthly_interest_rate
  FROM (VALUES
        (:'televisor'::uuid, :'tarjeta'::uuid,  2400000::numeric, 'Televisor', 2, 15),
        (:'celular'::uuid,   :'visa_usd'::uuid,     600::numeric, 'Celular',   3,  5)
       ) AS p(id, account_id, amount, description, meses, dia)
  JOIN finance.accounts a ON a.id = p.account_id
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = 'Tecnología';

INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate, amount_base,
     description, occurred_at, origin, installment_purchase_id, installment_number, installment_principal)
SELECT gen_random_uuid(), p.user_id, p.account_id, p.category_id, 'EXPENSE', c.monto, p.currency_code, 1, c.monto,
       p.description,
       (date_trunc('month', p.purchase_date) + make_interval(months => k, days => a.payment_due_day - 1))::timestamp
           AT TIME ZONE 'America/Bogota',
       'WEB', p.id, k, p.amount / 6
  FROM finance.installment_purchases p
  JOIN finance.accounts a ON a.id = p.account_id
  JOIN finance.currencies m ON m.code = p.currency_code
 CROSS JOIN generate_series(1, 6) k
 CROSS JOIN LATERAL (SELECT p.amount / 6 + round(p.amount * p.monthly_interest_rate / 100
                                                 * (7 - k) / 6, m.decimal_places) AS monto) c
 WHERE p.id IN (:'televisor'::uuid, :'celular'::uuid);


-- -----------------------------------------------------------------------------
-- Gasto que llego en dolares a una cuenta en pesos (FIN-96): una compra en linea
-- en la Mastercard, ya aprobada. amount es lo recibido llevado a COP con la tasa
-- de la base para su fecha y redondeado a pesos; original_amount y
-- original_currency_code guardan lo recibido, y conserva el grupo con que entro
-- pendiente.
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate, amount_base,
     original_amount, original_currency_code, description, occurred_at, status, origin, group_id)
SELECT gen_random_uuid(), :'uid'::uuid, a.id, c.id, 'EXPENSE', v.monto, a.currency_code, 1, v.monto,
       45.99, 'USD', 'Compra en tienda en línea', t.occurred_at, 'CONFIRMED', 'WEB', gen_random_uuid()
  FROM finance.accounts a
 CROSS JOIN LATERAL (
        SELECT (date_trunc('month', now() AT TIME ZONE 'America/Bogota')
                - INTERVAL '1 month' + INTERVAL '8 day 15 hours') AT TIME ZONE 'America/Bogota' AS occurred_at
       ) AS t
  JOIN finance.currencies m ON m.code = a.currency_code
 CROSS JOIN LATERAL (
        SELECT round(finance.amount_in_currency(a.currency_code, 'USD', 45.99,
                                                (t.occurred_at AT TIME ZONE 'America/Bogota')::date),
                     m.decimal_places) AS monto
       ) AS v
  JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = 'Tecnología'
 WHERE a.id = :'tarjeta'::uuid;


-- -----------------------------------------------------------------------------
-- Personas afiliadas (FIN-30)
--
-- Carlos y Valentina, afiliados; Andres, con una solicitud sin responder; y una
-- solicitud abierta a Sofia, que no tiene cuenta. Misma contrasena que el
-- usuario, una cuenta en COP y la copia de las categorias, como el registro.
-- Fechas en dias hacia atras desde now(), no por mes: todo queda en el pasado y
-- los pendientes salen como recientes.
-- -----------------------------------------------------------------------------
INSERT INTO finance.users (id, email, password_hash, first_name, last_name, created_at)
VALUES
    (:'carlos'::uuid,    :'carlos_email',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy', 'Carlos',    'Ruiz',   now() - INTERVAL '72 days'),
    (:'valentina'::uuid, :'valentina_email',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy', 'Valentina', 'Torres', now() - INTERVAL '62 days'),
    (:'andres'::uuid,    :'andres_email',
     '$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy', 'Andrés',    'Mejía',  now() - INTERVAL '5 days');

INSERT INTO finance.categories
    (id, user_id, name, applies_to, icon, color, sort_order, is_system)
SELECT gen_random_uuid(), p.id, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
  FROM finance.default_categories d
 CROSS JOIN (VALUES (:'carlos'::uuid), (:'valentina'::uuid), (:'andres'::uuid)) AS p(id)
 WHERE d.is_active;

INSERT INTO finance.accounts (id, user_id, name, type, currency_code, initial_balance)
VALUES
    (:'cta_carlos'::uuid,    :'carlos'::uuid,    'Bancolombia', 'DEBIT', 'COP', 2000000),
    (:'cta_valentina'::uuid, :'valentina'::uuid, 'Davivienda',  'DEBIT', 'COP', 2500000),
    (:'cta_andres'::uuid,    :'andres'::uuid,    'Nequi',       'DEBIT', 'COP',  500000);

-- updated_at es cuando se acepto: el saldo por persona ordena por ella.
INSERT INTO finance.affiliations
    (id, requester_id, addressee_email, addressee_id, status, created_at, updated_at)
VALUES
    (:'af_carlos'::uuid,    :'uid'::uuid,       :'carlos_email', :'carlos'::uuid, 'ACCEPTED',
     now() - INTERVAL '70 days', now() - INTERVAL '69 days'),
    (:'af_valentina'::uuid, :'valentina'::uuid, :'email',        :'uid'::uuid,    'ACCEPTED',
     now() - INTERVAL '60 days', now() - INTERVAL '59 days'),
    (:'af_andres'::uuid,    :'andres'::uuid,    :'email',        :'uid'::uuid,    'REQUESTED',
     now() - INTERVAL '3 days',  now() - INTERVAL '3 days'),
    (:'af_sofia'::uuid,     :'uid'::uuid,       :'sofia_email',  NULL,            'REQUESTED',
     now() - INTERVAL '20 days', now() - INTERVAL '20 days');


-- -----------------------------------------------------------------------------
-- Lo compartido (FIN-30)
--
-- Las filas que el API habria dejado (TransactionR2dbcAdapter, PartesDelAlta,
-- ApprovePendingTransactionUseCase): si cambia como las escribe, esto se queda
-- viejo sin que nada falle. Todo en COP.
--   G5 hace 50 d  Valentina paga la cabaña; la parte del usuario, pagada
--   G1 hace 35 d  el usuario paga el asado; Carlos y Valentina pagan su parte,
--                 y solo el ingreso de Carlos esta aprobado
--   G2 hace 12 d  el usuario paga el mercado; Carlos debe, Valentina rechazo
--   G6 hace  8 d  Valentina paga el taxi; el usuario rechazo su parte
--   G4 hace  6 d  Carlos paga las boletas; el usuario debe
--   G3 hace  4 d  el usuario paga la pizza; Valentina debe
--   T1 hace 18 d y T2 hace 2 d: transferencias compartidas, pendientes para
--                 quien las recibe
-- -----------------------------------------------------------------------------

-- Gastos de quien pago: los que llevan partes.
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, created_at)
SELECT g.id, g.user_id, g.account_id, c.id, 'EXPENSE', g.amount, 'COP',
       1, g.amount, g.description, now() - g.hace, now() - g.hace
  FROM (VALUES
        (:'g5'::uuid, :'valentina'::uuid, :'cta_valentina'::uuid, 'Viajes',          900000::numeric, 'Arriendo de la cabaña', INTERVAL '50 days'),
        (:'g1'::uuid, :'uid'::uuid,       :'tarjeta'::uuid,       'Restaurantes',    240000::numeric, 'Asado en la finca',     INTERVAL '35 days'),
        (:'g2'::uuid, :'uid'::uuid,       :'tarjeta'::uuid,       'Mercado',         180000::numeric, 'Mercado para el paseo', INTERVAL '12 days'),
        (:'g6'::uuid, :'valentina'::uuid, :'cta_valentina'::uuid, 'Transporte',       64000::numeric, 'Taxi al aeropuerto',    INTERVAL '8 days'),
        (:'g4'::uuid, :'carlos'::uuid,    :'cta_carlos'::uuid,    'Entretenimiento', 300000::numeric, 'Boletas del partido',   INTERVAL '6 days'),
        (:'g3'::uuid, :'uid'::uuid,       :'efectivo'::uuid,      'Restaurantes',     72000::numeric, 'Pizza',                 INTERVAL '4 days')
       ) AS g(id, user_id, account_id, category_name, amount, description, hace)
  JOIN finance.categories c
    ON c.user_id = g.user_id AND c.name = g.category_name;

-- Las partes copian la descripcion y la fecha del gasto. resuelta: cuando quien
-- debe la pago o la rechazo.
INSERT INTO finance.expense_shares
    (id, source_transaction_id, payer_id, debtor_id, amount, currency_code, description,
     occurred_at, status, resolved_at, created_at, updated_at)
SELECT s.id, t.id, t.user_id, s.debtor_id, s.amount, t.currency_code, t.description,
       t.occurred_at, s.status, now() - s.resuelta, t.occurred_at, COALESCE(now() - s.resuelta, t.occurred_at)
  FROM (VALUES
        (:'s1'::uuid, :'g5'::uuid, :'uid'::uuid,       300000::numeric, 'PAID',     INTERVAL '47 days'),
        (:'s2'::uuid, :'g1'::uuid, :'carlos'::uuid,     80000::numeric, 'PAID',     INTERVAL '30 days'),
        (:'s3'::uuid, :'g1'::uuid, :'valentina'::uuid,  80000::numeric, 'PAID',     INTERVAL '33 days'),
        (:'s4'::uuid, :'g2'::uuid, :'carlos'::uuid,     60000::numeric, 'PENDING',  NULL::interval),
        (:'s5'::uuid, :'g2'::uuid, :'valentina'::uuid,  60000::numeric, 'REJECTED', INTERVAL '10 days'),
        (:'s6'::uuid, :'g6'::uuid, :'uid'::uuid,        32000::numeric, 'REJECTED', INTERVAL '7 days'),
        (:'s7'::uuid, :'g4'::uuid, :'uid'::uuid,       150000::numeric, 'PENDING',  NULL::interval),
        (:'s8'::uuid, :'g3'::uuid, :'valentina'::uuid,  36000::numeric, 'PENDING',  NULL::interval)
       ) AS s(id, source_id, debtor_id, amount, status, resuelta)
  JOIN finance.transactions t
    ON t.id = s.source_id;

-- Lo que la parte le deja a quien debe, con la fecha del gasto y un grupo propio:
-- pendiente sin cuenta ni categoria mientras no la paga, confirmado en la cuenta
-- y la categoria que eligio al pagarla. La rechazada no deja nada: rechazar
-- borra el pendiente.
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate, amount_base,
     description, occurred_at, status, group_id, shared_by_user_id, share_id, created_at)
SELECT gen_random_uuid(), s.debtor_id, e.account_id, c.id, 'EXPENSE', s.amount, s.currency_code, 1, s.amount,
       s.description, s.occurred_at, CASE s.status WHEN 'PAID' THEN 'CONFIRMED' ELSE 'PENDING' END,
       gen_random_uuid(), s.payer_id, s.id, s.occurred_at
  FROM finance.expense_shares s
  LEFT JOIN (VALUES
        (:'s1'::uuid, :'nomina'::uuid,        'Viajes'),
        (:'s2'::uuid, :'cta_carlos'::uuid,    'Restaurantes'),
        (:'s3'::uuid, :'cta_valentina'::uuid, 'Restaurantes')
       ) AS e(share_id, account_id, category_name)
    ON e.share_id = s.id
  LEFT JOIN finance.categories c
    ON c.user_id = s.debtor_id AND c.name = e.category_name
 WHERE :'uid'::uuid IN (s.payer_id, s.debtor_id)
   AND s.status IN ('PENDING', 'PAID');

-- Pagar una parte le crea a quien pago un ingreso pendiente con la fecha del
-- pago, que queda confirmado si ya lo aprobo.
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate, amount_base,
     description, occurred_at, status, group_id, shared_by_user_id, share_id, created_at)
SELECT gen_random_uuid(), s.payer_id, i.account_id, c.id, 'INCOME', s.amount, s.currency_code, 1, s.amount,
       s.description, s.resolved_at, CASE WHEN i.account_id IS NULL THEN 'PENDING' ELSE 'CONFIRMED' END,
       gen_random_uuid(), s.debtor_id, s.id, s.resolved_at
  FROM finance.expense_shares s
  LEFT JOIN (VALUES
        (:'s1'::uuid, :'cta_valentina'::uuid, 'Reembolsos'),
        (:'s2'::uuid, :'nomina'::uuid,        'Reembolsos')
       ) AS i(share_id, account_id, category_name)
    ON i.share_id = s.id
  LEFT JOIN finance.categories c
    ON c.user_id = s.payer_id AND c.name = i.category_name
 WHERE :'uid'::uuid IN (s.payer_id, s.debtor_id)
   AND s.status = 'PAID';

-- Transferencia compartida: un gasto de quien transfiere, sin partes, y para
-- quien recibe un ingreso pendiente sin parte y con un grupo propio.
INSERT INTO finance.transactions
    (id, user_id, account_id, category_id, type, amount, currency_code, exchange_rate, amount_base,
     description, occurred_at, status, group_id, shared_by_user_id, created_at)
SELECT gen_random_uuid(), m.user_id, m.account_id, c.id, m.type, f.amount, 'COP', 1, f.amount,
       f.description, now() - f.hace, m.status, m.group_id, m.shared_by, now() - f.hace
  FROM (VALUES
        (:'uid'::uuid,       :'nomina'::uuid,        'Otros gastos', :'carlos'::uuid, 100000::numeric, 'Préstamo',            INTERVAL '18 days'),
        (:'valentina'::uuid, :'cta_valentina'::uuid, 'Regalos',      :'uid'::uuid,     50000::numeric, 'Mi parte del regalo', INTERVAL '2 days')
       ) AS f(de, cuenta, categoria, para, amount, description, hace)
 CROSS JOIN LATERAL (VALUES
        (f.de,   f.cuenta,   f.categoria, 'EXPENSE', 'CONFIRMED', NULL::uuid,        NULL::uuid),
        (f.para, NULL::uuid, NULL,        'INCOME',  'PENDING',   gen_random_uuid(), f.de)
       ) AS m(user_id, account_id, category_name, type, status, group_id, shared_by)
  LEFT JOIN finance.categories c
    ON c.user_id = m.user_id AND c.name = m.category_name;


-- -----------------------------------------------------------------------------
-- Pendientes del asistente (FIN-95)
--
-- Las filas que deja el alta en lote del asistente (PendienteDelAsistenteAdapter):
-- origen TELEGRAM, cuenta y categoria resueltas, un grupo por mensaje y sin
-- persona que los comparta. Sin fecha dictada, occurred_at es el instante del
-- mensaje; con fecha, ese dia a las 12:00 en Bogota. Por dias hacia atras desde
-- now(), como lo compartido.
--   A hace 1 dia    mercado y gasolina en la tarjeta (la gasolina con fecha
--                   dictada, dos dias antes) y una recarga de la nomina a Nequi
--   B hace 3 horas  almuerzo en efectivo
-- -----------------------------------------------------------------------------
INSERT INTO finance.transactions
    (id, user_id, account_id, destination_account_id, category_id, type, amount, currency_code,
     exchange_rate, amount_base, description, occurred_at, status, origin, group_id, created_at)
SELECT gen_random_uuid(), :'uid'::uuid, p.account_id, p.destination_id, c.id, p.type, p.amount, 'COP',
       1, p.amount, p.description,
       COALESCE((((now() - p.mensaje) AT TIME ZONE 'America/Bogota')::date - p.dias_antes + TIME '12:00')
                    AT TIME ZONE 'America/Bogota',
                now() - p.mensaje),
       'PENDING', 'TELEGRAM', p.group_id, now() - p.mensaje
  FROM (VALUES
        (:'grupo_a'::uuid, :'tarjeta'::uuid,  NULL::uuid,     'Mercado',      'EXPENSE',   86400::numeric, 'Mercado del fin de semana', INTERVAL '1 day',   NULL::int),
        (:'grupo_a'::uuid, :'tarjeta'::uuid,  NULL::uuid,     'Transporte',   'EXPENSE',  120000::numeric, 'Gasolina',                  INTERVAL '1 day',   2),
        (:'grupo_a'::uuid, :'nomina'::uuid,   :'nequi'::uuid, NULL,           'TRANSFER', 200000::numeric, 'Recarga Nequi',             INTERVAL '1 day',   NULL),
        (:'grupo_b'::uuid, :'efectivo'::uuid, NULL::uuid,     'Restaurantes', 'EXPENSE',   28000::numeric, 'Almuerzo',                  INTERVAL '3 hours', NULL)
       ) AS p(group_id, account_id, destination_id, category_name, type, amount, description, mensaje, dias_antes)
  LEFT JOIN finance.categories c
    ON c.user_id = :'uid'::uuid AND c.name = p.category_name;


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
SELECT COALESCE(p.first_name || ' ' || p.last_name, a.addressee_email) AS persona,
       a.status AS afiliacion,
       CASE WHEN a.requester_id = :'uid'::uuid THEN 'enviada' ELSE 'recibida' END AS solicitud,
       COALESCE(sum(s.amount) FILTER (WHERE s.payer_id = :'uid'::uuid), 0)  AS le_debe,
       COALESCE(sum(s.amount) FILTER (WHERE s.debtor_id = :'uid'::uuid), 0) AS le_adeuda
  FROM finance.affiliations a
  LEFT JOIN finance.users p
    ON p.id = CASE WHEN a.requester_id = :'uid'::uuid THEN a.addressee_id ELSE a.requester_id END
  LEFT JOIN finance.expense_shares s
    ON s.status = 'PENDING'
   AND p.id IN (s.payer_id, s.debtor_id)
   AND :'uid'::uuid IN (s.payer_id, s.debtor_id)
 WHERE :'uid'::uuid IN (a.requester_id, a.addressee_id)
 GROUP BY a.id, p.first_name, p.last_name
 ORDER BY a.created_at DESC;
SELECT group_id AS grupo_del_asistente, count(*) AS pendientes, sum(amount) AS monto,
       min(created_at AT TIME ZONE 'America/Bogota')::timestamp(0) AS mensaje
  FROM finance.transactions
 WHERE user_id = :'uid'::uuid
   AND status = 'PENDING'
   AND shared_by_user_id IS NULL
 GROUP BY group_id
 ORDER BY mensaje;
