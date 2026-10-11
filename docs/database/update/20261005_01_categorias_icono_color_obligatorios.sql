-- =============================================================================
-- 2026-10-05 · 01 · Icono y color obligatorios en las categorías (FA-66)
--
-- El alta de categorías exige icono y color desde FA-66. Las filas que ya
-- existían sin ellos se rellenan con los de "Otros gastos" de la semilla
-- (ellipsis, #757575) y las dos columnas pasan a NOT NULL, también en la
-- semilla: el registro la copia a categories, y una fila sin icono haría
-- fallar cada registro.
--
-- Un UPDATE por columna, para que una fila con icono y sin color conserve su
-- icono. Incluye las categorías borradas: NOT NULL aplica a todas las filas.
--
-- En producción se aplica DESPUÉS de desplegar la app de FA-66: con la base
-- nueva y la app vieja, un alta sin icono saldría como 500.
--
--   psql -U <usuario> -d financeapp -f 20261005_01_categorias_icono_color_obligatorios.sql
-- =============================================================================

BEGIN;

UPDATE finance.default_categories SET icon  = 'ellipsis' WHERE icon  IS NULL;
UPDATE finance.default_categories SET color = '#757575'  WHERE color IS NULL;

UPDATE finance.categories SET icon  = 'ellipsis' WHERE icon  IS NULL;
UPDATE finance.categories SET color = '#757575'  WHERE color IS NULL;

ALTER TABLE finance.default_categories
    ALTER COLUMN icon  SET NOT NULL,
    ALTER COLUMN color SET NOT NULL;

ALTER TABLE finance.categories
    ALTER COLUMN icon  SET NOT NULL,
    ALTER COLUMN color SET NOT NULL;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Quita las restricciones; los valores rellenados se quedan, porque
-- no hay forma de distinguirlos de un "ellipsis" elegido a propósito.
--
-- BEGIN;
-- ALTER TABLE finance.categories
--     ALTER COLUMN icon  DROP NOT NULL,
--     ALTER COLUMN color DROP NOT NULL;
-- ALTER TABLE finance.default_categories
--     ALTER COLUMN icon  DROP NOT NULL,
--     ALTER COLUMN color DROP NOT NULL;
-- COMMIT;
-- -----------------------------------------------------------------------------
