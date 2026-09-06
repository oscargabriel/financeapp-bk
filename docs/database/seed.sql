-- =============================================================================
-- financeapp-bk : datos semilla
-- Se ejecuta después de schema.sql y es idempotente: volver a correrlo no
-- duplica filas ni pisa ediciones manuales del catálogo.
--
-- Ejecutar:  psql -U <usuario> -d financeapp -f seed.sql
-- =============================================================================

SET search_path TO finance, public;


-- =============================================================================
-- MONEDAS
-- =============================================================================

INSERT INTO finance.currencies (code, name, symbol, decimal_places) VALUES
    ('COP', 'Peso colombiano',  '$',   0),
    ('USD', 'Dólar estadounidense', 'US$', 2),
    ('EUR', 'Euro',             '€',   2),
    ('MXN', 'Peso mexicano',    'MX$', 2),
    ('VES', 'Bolívar venezolano', 'Bs.', 2),
    ('ARS', 'Peso argentino',   'AR$', 2),
    ('CLP', 'Peso chileno',     'CL$', 0),
    ('PEN', 'Sol peruano',      'S/',  2),
    ('BRL', 'Real brasileño',   'R$',  2)
ON CONFLICT (code) DO NOTHING;


-- =============================================================================
-- CATEGORÍAS POR DEFECTO
--
-- Se copian a cada usuario al registrarse (ver la consulta al final del
-- archivo). Cambiar esta tabla no altera las categorías de usuarios ya creados.
-- =============================================================================

INSERT INTO finance.default_categories (name, applies_to, icon, color, sort_order) VALUES
    ('Mercado',          'EXPENSE', 'shopping-cart',  '#2E7D32',  10),
    ('Restaurantes',     'EXPENSE', 'utensils',       '#EF6C00',  20),
    ('Transporte',       'EXPENSE', 'car',            '#1565C0',  30),
    ('Vivienda',         'EXPENSE', 'home',           '#6A1B9A',  40),
    ('Servicios',        'EXPENSE', 'plug',           '#00838F',  50),
    ('Salud',            'EXPENSE', 'heart-pulse',    '#C62828',  60),
    ('Educación',        'EXPENSE', 'graduation-cap', '#283593',  70),
    ('Entretenimiento',  'EXPENSE', 'film',           '#AD1457',  80),
    ('Ropa',             'EXPENSE', 'shirt',          '#4E342E',  90),
    ('Tecnología',       'EXPENSE', 'laptop',         '#37474F', 100),
    ('Suscripciones',    'EXPENSE', 'repeat',         '#5E35B1', 110),
    ('Mascotas',         'EXPENSE', 'paw-print',      '#795548', 120),
    ('Viajes',           'EXPENSE', 'plane',          '#0277BD', 130),
    ('Regalos',          'EXPENSE', 'gift',           '#D81B60', 140),
    ('Impuestos',        'EXPENSE', 'landmark',       '#455A64', 150),
    ('Otros gastos',     'EXPENSE', 'ellipsis',       '#757575', 160),
    ('Salario',          'INCOME',  'wallet',         '#1B5E20', 200),
    ('Freelance',        'INCOME',  'briefcase',      '#33691E', 210),
    ('Ventas',           'INCOME',  'tag',            '#00695C', 220),
    ('Intereses',        'INCOME',  'trending-up',    '#004D40', 230),
    ('Reembolsos',       'INCOME',  'undo',           '#558B2F', 240),
    ('Otros ingresos',   'INCOME',  'ellipsis',       '#757575', 250)
ON CONFLICT (name) DO NOTHING;


-- =============================================================================
-- COPIA DE LA SEMILLA A UN USUARIO
--
-- Esto lo ejecuta la aplicación dentro de la misma transacción del registro.
-- Queda aquí como referencia y para poblar usuarios creados a mano.
-- Los UUID los genera la app (v7); en psql se usa gen_random_uuid().
--
--   INSERT INTO finance.categories
--       (id, user_id, name, applies_to, icon, color, sort_order, is_system)
--   SELECT gen_random_uuid(), :'user_id', d.name, d.applies_to,
--          d.icon, d.color, d.sort_order, TRUE
--     FROM finance.default_categories d
--    WHERE d.is_active
--   ON CONFLICT DO NOTHING;
-- =============================================================================
