-- =============================================================================
-- financeapp-bk : construcción completa del esquema desde cero
-- PostgreSQL 14+
--
-- Este archivo es la fuente de verdad del modelo. Cada cambio posterior se
-- aplica AQUÍ y además se emite como script incremental en update/.
--
-- Ejecutar:  psql -U <usuario> -d financeapp -f schema.sql
--            psql -U <usuario> -d financeapp -f seed.sql
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance, public;


-- =============================================================================
-- FUNCIONES DE SOPORTE
-- =============================================================================

CREATE OR REPLACE FUNCTION finance.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- Delta que un movimiento aplica sobre la cuenta ORIGEN. El monto siempre se
-- guarda positivo: el signo lo determina el tipo, nunca el dato.
CREATE OR REPLACE FUNCTION finance.balance_delta(p_type VARCHAR, p_amount NUMERIC)
RETURNS NUMERIC
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE WHEN p_type = 'INCOME' THEN p_amount ELSE -p_amount END;
$$;


-- =============================================================================
-- CURRENCIES
-- =============================================================================

CREATE TABLE finance.currencies (
    code            CHAR(3)      PRIMARY KEY,
    name            VARCHAR(60)  NOT NULL,
    symbol          VARCHAR(6)   NOT NULL,
    decimal_places  SMALLINT     NOT NULL DEFAULT 2,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_currencies_code    CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currencies_decimal CHECK (decimal_places BETWEEN 0 AND 6)
);

COMMENT ON TABLE  finance.currencies IS 'Catálogo ISO 4217 de monedas soportadas.';
COMMENT ON COLUMN finance.currencies.decimal_places IS 'Decimales de presentación (COP y CLP usan 0, USD y EUR usan 2). No afecta la precisión de almacenamiento.';

CREATE TRIGGER trg_currencies_updated_at
    BEFORE UPDATE ON finance.currencies
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- =============================================================================
-- EXCHANGE_RATES
-- =============================================================================

CREATE TABLE finance.exchange_rates (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency_code  CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    to_currency_code    CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    rate                NUMERIC(20,10) NOT NULL,
    rate_date           DATE           NOT NULL,
    source              VARCHAR(40)    NOT NULL DEFAULT 'MANUAL',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_exchange_rates_rate      CHECK (rate > 0),
    CONSTRAINT ck_exchange_rates_distinct  CHECK (from_currency_code <> to_currency_code),
    CONSTRAINT ck_exchange_rates_source    CHECK (source IN ('MANUAL', 'API', 'IMPORT')),
    CONSTRAINT ux_exchange_rates_pair_date UNIQUE (from_currency_code, to_currency_code, rate_date)
);

COMMENT ON TABLE  finance.exchange_rates IS 'Tasa de cambio por par de monedas y fecha. Se consulta la fila con rate_date más reciente <= fecha del movimiento.';
COMMENT ON COLUMN finance.exchange_rates.rate IS 'Unidades de to_currency que equivalen a 1 unidad de from_currency.';

CREATE INDEX ix_exchange_rates_lookup
    ON finance.exchange_rates (from_currency_code, to_currency_code, rate_date DESC);


-- =============================================================================
-- USERS
-- =============================================================================

CREATE TABLE finance.users (
    id                  UUID         PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100),
    phone               VARCHAR(20),
    birth_date          DATE,
    telegram_chat_id    BIGINT,
    base_currency_code  CHAR(3)      NOT NULL DEFAULT 'COP' REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'America/Bogota',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email     CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[A-Za-z]{2,}$'),
    -- Postgres exige funciones IMMUTABLE en un CHECK, así que aquí solo cabe un
    -- límite fijo; que la fecha no sea futura lo valida la aplicación.
    CONSTRAINT ck_users_birthdate CHECK (birth_date IS NULL OR birth_date > DATE '1900-01-01')
);

COMMENT ON TABLE  finance.users IS 'Persona que registra finanzas. La autenticación es propia (email + hash); Telegram es un canal adicional que se vincula después.';
COMMENT ON COLUMN finance.users.password_hash IS 'Hash BCrypt/Argon2 calculado en la aplicación. Jamás la contraseña en claro.';
COMMENT ON COLUMN finance.users.base_currency_code IS 'Moneda en la que se consolidan los reportes del usuario (columna amount_base de transactions).';
COMMENT ON COLUMN finance.users.timezone IS 'Zona IANA. Determina dónde corta el mes en las vistas de gasto: un gasto del 30 a las 21:00 en Bogotá no puede caer en el mes siguiente.';

-- Únicos parciales: una fila borrada lógicamente libera el email y el chat de Telegram.
CREATE UNIQUE INDEX ux_users_email
    ON finance.users (lower(email)) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_users_telegram_chat_id
    ON finance.users (telegram_chat_id) WHERE telegram_chat_id IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON finance.users
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- =============================================================================
-- ACCOUNTS
-- =============================================================================

CREATE TABLE finance.accounts (
    id               UUID           PRIMARY KEY,
    user_id          UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    name             VARCHAR(80)    NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    currency_code    CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    initial_balance  NUMERIC(18,4)  NOT NULL DEFAULT 0,
    current_balance  NUMERIC(18,4)  NOT NULL DEFAULT 0,
    credit_limit     NUMERIC(18,4),
    statement_day    SMALLINT,
    payment_due_day  SMALLINT,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CASH', 'DEBIT', 'CREDIT', 'SAVINGS', 'INVESTMENT', 'OTHER')),
    CONSTRAINT ck_accounts_credit_limit    CHECK (credit_limit IS NULL OR credit_limit > 0),
    CONSTRAINT ck_accounts_statement_day   CHECK (statement_day IS NULL OR statement_day BETWEEN 1 AND 31),
    CONSTRAINT ck_accounts_payment_due_day CHECK (payment_due_day IS NULL OR payment_due_day BETWEEN 1 AND 31),
    -- Los atributos de crédito solo tienen sentido en una tarjeta de crédito.
    CONSTRAINT ck_accounts_credit_fields CHECK (
        type = 'CREDIT'
        OR (credit_limit IS NULL AND statement_day IS NULL AND payment_due_day IS NULL)
    )
);

COMMENT ON TABLE  finance.accounts IS 'Origen o destino del dinero: efectivo, cuenta bancaria, tarjeta de crédito, etc.';
COMMENT ON COLUMN finance.accounts.current_balance IS 'Saldo vigente. Lo mantiene el trigger trg_transactions_sync_balance; la aplicación NUNCA lo escribe directamente. En una tarjeta de crédito un valor negativo es la deuda, y el cupo disponible es credit_limit + current_balance.';
COMMENT ON COLUMN finance.accounts.initial_balance IS 'Saldo con el que la cuenta entra al sistema. Se copia a current_balance al crearla.';

CREATE UNIQUE INDEX ux_accounts_user_name
    ON finance.accounts (user_id, lower(name)) WHERE deleted_at IS NULL;

CREATE INDEX ix_accounts_user
    ON finance.accounts (user_id) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON finance.accounts
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();

-- El saldo inicial es el punto de partida del saldo vigente.
CREATE OR REPLACE FUNCTION finance.seed_account_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.current_balance := NEW.initial_balance;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_seed_balance
    BEFORE INSERT ON finance.accounts
    FOR EACH ROW EXECUTE FUNCTION finance.seed_account_balance();


-- =============================================================================
-- DEFAULT_CATEGORIES  (semilla del catálogo)
-- =============================================================================

CREATE TABLE finance.default_categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(60)  NOT NULL,
    applies_to  VARCHAR(10)  NOT NULL,
    icon        VARCHAR(40),
    color       CHAR(7),
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_default_categories_name  UNIQUE (name),
    CONSTRAINT ck_default_categories_scope CHECK (applies_to IN ('EXPENSE', 'INCOME', 'BOTH')),
    CONSTRAINT ck_default_categories_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

COMMENT ON TABLE finance.default_categories IS 'Categorías semilla que se copian a cada usuario al registrarse. Editar esta tabla NO afecta a los usuarios ya creados.';

CREATE TRIGGER trg_default_categories_updated_at
    BEFORE UPDATE ON finance.default_categories
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- =============================================================================
-- CATEGORIES  (las del usuario)
-- =============================================================================

CREATE TABLE finance.categories (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    applies_to  VARCHAR(10)  NOT NULL DEFAULT 'EXPENSE',
    icon        VARCHAR(40),
    color       CHAR(7),
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_categories_scope CHECK (applies_to IN ('EXPENSE', 'INCOME', 'BOTH')),
    CONSTRAINT ck_categories_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

COMMENT ON TABLE  finance.categories IS 'Categorías propias de cada usuario. Al registrarse se copian desde default_categories y desde ahí las edita libremente.';
COMMENT ON COLUMN finance.categories.is_system IS 'TRUE si la fila nació de la semilla. Solo informativo: el usuario puede editarla o borrarla igual.';
COMMENT ON COLUMN finance.categories.deleted_at IS 'Borrado lógico: una categoría con movimientos históricos no puede desaparecer sin romper los reportes.';

CREATE UNIQUE INDEX ux_categories_user_name
    ON finance.categories (user_id, lower(name)) WHERE deleted_at IS NULL;

CREATE INDEX ix_categories_user_scope
    ON finance.categories (user_id, applies_to) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON finance.categories
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- =============================================================================
-- TRANSACTIONS
-- =============================================================================

CREATE TABLE finance.transactions (
    id                      UUID           PRIMARY KEY,
    user_id                 UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    -- NO ACTION diferido, no RESTRICT: impide igual borrar una cuenta o categoría
    -- con movimientos, pero se verifica al final de la transacción, de modo que
    -- el borrado en cascada de un usuario no choque con el orden de las cascadas.
    account_id              UUID           NOT NULL REFERENCES finance.accounts (id)   ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    destination_account_id  UUID                    REFERENCES finance.accounts (id)   ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    category_id             UUID                    REFERENCES finance.categories (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    type                    VARCHAR(10)    NOT NULL,
    amount                  NUMERIC(18,4)  NOT NULL,
    destination_amount      NUMERIC(18,4),
    currency_code           CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    exchange_rate           NUMERIC(20,10) NOT NULL DEFAULT 1,
    amount_base             NUMERIC(18,4)  NOT NULL,
    description             VARCHAR(255)   NOT NULL,
    notes                   TEXT,
    occurred_at             TIMESTAMPTZ    NOT NULL,
    origin                  VARCHAR(10)    NOT NULL DEFAULT 'WEB',
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_type        CHECK (type IN ('EXPENSE', 'INCOME', 'TRANSFER')),
    CONSTRAINT ck_transactions_origin      CHECK (origin IN ('WEB', 'TELEGRAM', 'IMPORT')),
    CONSTRAINT ck_transactions_amount      CHECK (amount > 0),
    CONSTRAINT ck_transactions_amount_base CHECK (amount_base > 0),
    CONSTRAINT ck_transactions_rate        CHECK (exchange_rate > 0),
    -- Una transferencia mueve dinero entre dos cuentas propias y no se categoriza;
    -- un gasto o ingreso siempre lleva categoría y no tiene cuenta destino.
    CONSTRAINT ck_transactions_shape CHECK (
        (type = 'TRANSFER'
            AND destination_account_id IS NOT NULL
            AND destination_account_id <> account_id
            AND category_id IS NULL)
        OR
        (type <> 'TRANSFER'
            AND destination_account_id IS NULL
            AND category_id IS NOT NULL)
    ),
    CONSTRAINT ck_transactions_destination_amount CHECK (
        destination_amount IS NULL
        OR (type = 'TRANSFER' AND destination_amount > 0)
    )
);

COMMENT ON TABLE  finance.transactions IS 'Todo movimiento de dinero. Se borran físicamente: no llevan borrado lógico.';
COMMENT ON COLUMN finance.transactions.amount IS 'Monto siempre positivo, en la moneda de la cuenta origen. El signo lo determina el tipo.';
COMMENT ON COLUMN finance.transactions.destination_amount IS 'Solo en TRANSFER entre cuentas de distinta moneda: lo que efectivamente entra al destino. NULL significa el mismo monto.';
COMMENT ON COLUMN finance.transactions.amount_base IS 'amount convertido a la moneda base del usuario. Es la columna que suman los reportes, para que monedas distintas sean comparables.';
COMMENT ON COLUMN finance.transactions.occurred_at IS 'Cuándo ocurrió el movimiento, no cuándo se registró (created_at). El bot de Telegram registra gastos de días anteriores.';

CREATE INDEX ix_transactions_user_date
    ON finance.transactions (user_id, occurred_at DESC);

CREATE INDEX ix_transactions_account_date
    ON finance.transactions (account_id, occurred_at DESC);

CREATE INDEX ix_transactions_destination_account
    ON finance.transactions (destination_account_id) WHERE destination_account_id IS NOT NULL;

CREATE INDEX ix_transactions_category_date
    ON finance.transactions (category_id, occurred_at DESC) WHERE category_id IS NOT NULL;

-- Soporta las vistas de gasto mensual, que solo miran los egresos.
CREATE INDEX ix_transactions_expense_report
    ON finance.transactions (user_id, occurred_at) WHERE type = 'EXPENSE';

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON finance.transactions
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- Mantiene accounts.current_balance. En UPDATE revierte el efecto de la fila
-- vieja antes de aplicar la nueva, para tolerar cambios de cuenta, tipo o monto.
CREATE OR REPLACE FUNCTION finance.sync_account_balances()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        UPDATE finance.accounts
           SET current_balance = current_balance - finance.balance_delta(OLD.type, OLD.amount)
         WHERE id = OLD.account_id;

        IF OLD.type = 'TRANSFER' THEN
            UPDATE finance.accounts
               SET current_balance = current_balance - COALESCE(OLD.destination_amount, OLD.amount)
             WHERE id = OLD.destination_account_id;
        END IF;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE finance.accounts
           SET current_balance = current_balance + finance.balance_delta(NEW.type, NEW.amount)
         WHERE id = NEW.account_id;

        IF NEW.type = 'TRANSFER' THEN
            UPDATE finance.accounts
               SET current_balance = current_balance + COALESCE(NEW.destination_amount, NEW.amount)
             WHERE id = NEW.destination_account_id;
        END IF;
    END IF;

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_transactions_sync_balance
    AFTER INSERT OR UPDATE OR DELETE ON finance.transactions
    FOR EACH ROW EXECUTE FUNCTION finance.sync_account_balances();


-- =============================================================================
-- BUDGETS
-- =============================================================================

CREATE TABLE finance.budgets (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    period_month   DATE           NOT NULL,
    category_id    UUID           REFERENCES finance.categories (id) ON DELETE CASCADE,
    amount         NUMERIC(18,4)  NOT NULL,
    currency_code  CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    notes          VARCHAR(255),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_budgets_amount CHECK (amount > 0),
    CONSTRAINT ck_budgets_period CHECK (period_month = date_trunc('month', period_month)::date)
);

COMMENT ON TABLE  finance.budgets IS 'Meta de gasto de un mes. La fila con category_id NULL es el tope global del mes; las demás son topes por categoría.';
COMMENT ON COLUMN finance.budgets.period_month IS 'Siempre el día 1 del mes (el CHECK lo garantiza), para que la comparación sea exacta.';
COMMENT ON COLUMN finance.budgets.amount IS 'Expresado en la moneda base del usuario, que es la unidad de amount_base en transactions.';

-- Dos únicos parciales porque en Postgres NULL no colisiona con NULL:
-- sin el segundo índice se podrían crear varias metas globales para el mismo mes.
CREATE UNIQUE INDEX ux_budgets_user_period_category
    ON finance.budgets (user_id, period_month, category_id) WHERE category_id IS NOT NULL;

CREATE UNIQUE INDEX ux_budgets_user_period_global
    ON finance.budgets (user_id, period_month) WHERE category_id IS NULL;

CREATE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON finance.budgets
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- =============================================================================
-- VISTAS DE GASTO MENSUAL
--
-- Reglas comunes:
--   * Solo cuentan los movimientos de tipo EXPENSE. Una transferencia mueve
--     dinero entre cuentas propias, no lo gasta.
--   * Suman amount_base para que monedas distintas sean comparables.
--   * El mes se corta en la zona horaria del usuario.
--   * FULL OUTER JOIN contra budgets: un mes con meta y sin gastos aparece con
--     total 0, y un mes con gastos y sin meta aparece con budget_amount NULL.
-- =============================================================================

CREATE OR REPLACE VIEW finance.v_monthly_spending AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           SUM(t.amount_base)                                               AS total_spent,
           COUNT(*)                                                         AS transaction_count
      FROM finance.transactions t
      JOIN finance.users u ON u.id = t.user_id
     WHERE t.type = 'EXPENSE'
     GROUP BY t.user_id, 2
),
budget AS (
    SELECT b.user_id,
           b.period_month,
           b.amount AS budget_amount
      FROM finance.budgets b
     WHERE b.category_id IS NULL
)
SELECT COALESCE(s.user_id, b.user_id)               AS user_id,
       COALESCE(s.period_month, b.period_month)     AS period_month,
       u.base_currency_code                         AS currency_code,
       COALESCE(s.total_spent, 0)                   AS total_spent,
       b.budget_amount,
       b.budget_amount - COALESCE(s.total_spent, 0) AS remaining,
       CASE WHEN b.budget_amount IS NOT NULL
            THEN ROUND(COALESCE(s.total_spent, 0) * 100 / b.budget_amount, 2)
       END                                          AS percent_used,
       COALESCE(s.transaction_count, 0)             AS transaction_count
  FROM spent s
  FULL OUTER JOIN budget b
    ON b.user_id = s.user_id
   AND b.period_month = s.period_month
  JOIN finance.users u
    ON u.id = COALESCE(s.user_id, b.user_id);

COMMENT ON VIEW finance.v_monthly_spending IS 'Una fila por usuario y mes: cuánto gastó, cuál era la meta, cuánto le queda y qué porcentaje consumió.';


CREATE OR REPLACE VIEW finance.v_monthly_spending_by_category AS
WITH spent AS (
    SELECT t.user_id,
           date_trunc('month', t.occurred_at AT TIME ZONE u.timezone)::date AS period_month,
           t.category_id,
           SUM(t.amount_base)                                               AS total_spent,
           COUNT(*)                                                         AS transaction_count
      FROM finance.transactions t
      JOIN finance.users u ON u.id = t.user_id
     WHERE t.type = 'EXPENSE'
     GROUP BY t.user_id, 2, t.category_id
),
budget AS (
    SELECT b.user_id,
           b.period_month,
           b.category_id,
           b.amount AS category_budget
      FROM finance.budgets b
     WHERE b.category_id IS NOT NULL
),
merged AS (
    SELECT COALESCE(s.user_id, b.user_id)           AS user_id,
           COALESCE(s.period_month, b.period_month) AS period_month,
           COALESCE(s.category_id, b.category_id)   AS category_id,
           COALESCE(s.total_spent, 0)               AS total_spent,
           COALESCE(s.transaction_count, 0)         AS transaction_count,
           b.category_budget
      FROM spent s
      FULL OUTER JOIN budget b
        ON b.user_id = s.user_id
       AND b.period_month = s.period_month
       AND b.category_id = s.category_id
)
SELECT m.user_id,
       m.period_month,
       m.category_id,
       c.name                            AS category_name,
       c.icon                            AS category_icon,
       c.color                           AS category_color,
       u.base_currency_code              AS currency_code,
       m.total_spent,
       m.category_budget,
       m.category_budget - m.total_spent AS remaining,
       CASE WHEN m.category_budget IS NOT NULL
            THEN ROUND(m.total_spent * 100 / m.category_budget, 2)
       END                               AS percent_used,
       ROUND(
           m.total_spent * 100
           / NULLIF(SUM(m.total_spent) OVER (PARTITION BY m.user_id, m.period_month), 0),
           2
       )                                 AS share_of_month,
       m.transaction_count
  FROM merged m
  JOIN finance.users u      ON u.id = m.user_id
  JOIN finance.categories c ON c.id = m.category_id;

COMMENT ON VIEW finance.v_monthly_spending_by_category IS 'Desglose del gasto del mes por categoría, con su tope si existe y el peso porcentual sobre el total gastado en el mes.';
