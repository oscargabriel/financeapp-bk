-- =============================================================================
-- 2026-10-09 · 04 · Series recurrentes (FA-107)
--
-- Tabla finance.recurrences con la plantilla, la regla y los contadores de cada
-- serie, y finance.transactions.recurrence_id para sus ocurrencias, que son
-- movimientos normales.
--
-- No toca filas: los movimientos existentes quedan con recurrence_id NULL.
--
-- En producción se aplica ANTES de desplegar la app de FA-107: la app nueva
-- lee recurrence_id en todas sus consultas de movimientos. La app vieja con la
-- base nueva sigue funcionando: no conoce la tabla ni la columna.
--
-- En local REINICIA LOS DATOS: modifica finance.transactions y test-data.sql
-- cambia. Después de aplicarlo, correr
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261009_04_series_recurrentes.sql
-- =============================================================================

BEGIN;

CREATE TABLE finance.recurrences (
    id                UUID           PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    account_id        UUID           NOT NULL REFERENCES finance.accounts (id)   ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    category_id       UUID           NOT NULL REFERENCES finance.categories (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    type              VARCHAR(10)    NOT NULL,
    amount            NUMERIC(18,4)  NOT NULL,
    currency_code     CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    description       VARCHAR(255)   NOT NULL,
    frequency         VARCHAR(10)    NOT NULL,
    interval_count    SMALLINT       NOT NULL DEFAULT 1,
    day_of_week       SMALLINT,
    day_of_month      SMALLINT,
    start_date        DATE           NOT NULL,
    end_date          DATE,
    occurrence_limit  SMALLINT,
    generated_count   INTEGER        NOT NULL DEFAULT 0,
    prior_count       INTEGER        NOT NULL DEFAULT 0,
    status            VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_recurrences_type      CHECK (type IN ('EXPENSE', 'INCOME')),
    CONSTRAINT ck_recurrences_amount    CHECK (amount > 0),
    CONSTRAINT ck_recurrences_frequency CHECK (frequency IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_recurrences_status    CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_recurrences_interval CHECK (
        (frequency = 'WEEKLY'  AND interval_count BETWEEN 1 AND 52)
        OR
        (frequency = 'MONTHLY' AND interval_count BETWEEN 1 AND 12)
    ),
    CONSTRAINT ck_recurrences_day CHECK (
        (frequency = 'WEEKLY'  AND day_of_week BETWEEN 1 AND 7 AND day_of_month IS NULL)
        OR
        (frequency = 'MONTHLY' AND day_of_month BETWEEN 1 AND 31 AND day_of_week IS NULL)
    ),
    CONSTRAINT ck_recurrences_end CHECK (end_date IS NULL OR occurrence_limit IS NULL),
    CONSTRAINT ck_recurrences_limit CHECK (occurrence_limit IS NULL OR occurrence_limit BETWEEN 1 AND 500),
    CONSTRAINT ck_recurrences_counts CHECK (generated_count >= 0 AND prior_count >= 0)
);

COMMENT ON TABLE  finance.recurrences IS 'Plantilla y regla de una serie. Sus ocurrencias son movimientos normales con recurrence_id; la fila se conserva al cancelar para que las pasadas sigan diciendo de qué serie son.';
COMMENT ON COLUMN finance.recurrences.start_date IS 'Inicio de la regla actual. Cambiar la periodicidad la reinicia en el día siguiente, en la zona del usuario.';
COMMENT ON COLUMN finance.recurrences.generated_count IS 'Ocurrencias de la regla actual ya creadas. La siguiente se crea desde aquí, no desde las que existen: una borrada a mano no vuelve.';
COMMENT ON COLUMN finance.recurrences.prior_count IS 'Ocurrencias de reglas anteriores que cuentan contra occurrence_limit.';
COMMENT ON COLUMN finance.recurrences.end_date IS 'Último día posible de una ocurrencia, incluido. NULL junto con occurrence_limit NULL es una serie sin fin: toda lectura de saldos o movimientos la pone al día.';

CREATE INDEX ix_recurrences_user_active
    ON finance.recurrences (user_id) WHERE status = 'ACTIVE';

CREATE TRIGGER trg_recurrences_updated_at
    BEFORE UPDATE ON finance.recurrences
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();

ALTER TABLE finance.transactions
    ADD COLUMN recurrence_id UUID REFERENCES finance.recurrences (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;

COMMENT ON COLUMN finance.transactions.recurrence_id IS 'Serie de la que el movimiento es ocurrencia (FA-107). Editarlo o borrarlo a mano no lo saca de ella.';

CREATE INDEX ix_transactions_recurrence
    ON finance.transactions (recurrence_id, occurred_at) WHERE recurrence_id IS NOT NULL;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Borra todas las series; sus ocurrencias se quedan como movimientos
-- sueltos.
-- -----------------------------------------------------------------------------
-- BEGIN;
-- DROP INDEX finance.ix_transactions_recurrence;
-- ALTER TABLE finance.transactions DROP COLUMN recurrence_id;
-- DROP TABLE finance.recurrences;
-- COMMIT;
