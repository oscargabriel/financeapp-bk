-- =============================================================================
-- 2026-10-13 · 01 · Afiliaciones, partes compartidas y pendientes sin cuenta (FIN-16)
--
-- affiliations guarda una fila por solicitud de afiliación entre dos personas,
-- con su estado. addressee_id solo se llena si el correo tenía cuenta al enviar
-- la solicitud: si no, nadie la recibe.
--
-- expense_shares guarda las partes de un gasto compartido: quién pagó, quién
-- debe, cuánto y en qué estado. La deuda abierta es la suma de las PENDING.
--
-- transactions admite pendientes sin cuenta ni categoría, sea cual sea su
-- origen. Un CONFIRMED sigue exigiendo las dos. shared_by_user_id dice quién
-- compartió el movimiento y share_id, de qué parte sale.
--
-- En producción va en cualquier orden respecto a la app: ninguna versión
-- desplegada nombra las tablas ni las columnas nuevas, y ninguna fila existente
-- queda sin cuenta. El porqué está en el design.md del change
-- fin-16-modelo-finanzas-compartidas.
--
-- Modifica transactions: en local, después de aplicarlo, recargar los datos:
--   psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
--
--   psql -U <usuario> -d financeapp -f 20261013_01_finanzas_compartidas.sql
-- =============================================================================

BEGIN;

CREATE TABLE finance.affiliations (
    id               UUID         PRIMARY KEY,
    requester_id     UUID         NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    addressee_email  VARCHAR(255) NOT NULL,
    addressee_id     UUID                  REFERENCES finance.users (id) ON DELETE CASCADE,
    status           VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_affiliations_status   CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'UNLINKED')),
    CONSTRAINT ck_affiliations_email    CHECK (addressee_email = lower(addressee_email)),
    CONSTRAINT ck_affiliations_not_self CHECK (addressee_id <> requester_id),
    -- Sin destinatario nadie la recibe: solo puede seguir abierta o cancelarse.
    CONSTRAINT ck_affiliations_unknown  CHECK (addressee_id IS NOT NULL OR status IN ('REQUESTED', 'CANCELLED'))
);

COMMENT ON TABLE  finance.affiliations IS 'Solicitud de afiliación entre dos personas (FIN-16). Cada solicitud es una fila: tras un rechazo, una cancelación o una desvinculación, la siguiente es otra fila.';
COMMENT ON COLUMN finance.affiliations.addressee_email IS 'Correo al que se envió, normalizado como en el registro. Se guarda aunque no tenga cuenta.';
COMMENT ON COLUMN finance.affiliations.addressee_id IS 'Usuario de addressee_email al enviar la solicitud. NULL si no tenía cuenta: nadie la recibe, y no se resuelve aunque la persona se registre después.';
COMMENT ON COLUMN finance.affiliations.status IS 'REQUESTED y ACCEPTED son las abiertas. REJECTED la decide el destinatario, CANCELLED el remitente y UNLINKED cualquiera de los dos sobre una aceptada.';

-- LEAST y GREATEST ignoran los NULL: sin el filtro de addressee_id, dos
-- solicitudes de A a correos no registrados distintos chocarían como (A, A).
CREATE UNIQUE INDEX ux_affiliations_open_pair
    ON finance.affiliations (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
 WHERE status IN ('REQUESTED', 'ACCEPTED') AND addressee_id IS NOT NULL;

CREATE UNIQUE INDEX ux_affiliations_open_email
    ON finance.affiliations (requester_id, addressee_email)
 WHERE status = 'REQUESTED';

CREATE INDEX ix_affiliations_requester ON finance.affiliations (requester_id);

CREATE INDEX ix_affiliations_addressee
    ON finance.affiliations (addressee_id) WHERE addressee_id IS NOT NULL;

CREATE TRIGGER trg_affiliations_updated_at
    BEFORE UPDATE ON finance.affiliations
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


CREATE TABLE finance.expense_shares (
    id                     UUID           PRIMARY KEY,
    source_transaction_id  UUID                    REFERENCES finance.transactions (id) ON DELETE SET NULL,
    payer_id               UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    debtor_id              UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    amount                 NUMERIC(18,4)  NOT NULL,
    currency_code          CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    description            VARCHAR(255)   NOT NULL,
    occurred_at            TIMESTAMPTZ    NOT NULL,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    resolved_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_expense_shares_amount   CHECK (amount > 0),
    CONSTRAINT ck_expense_shares_status   CHECK (status IN ('PENDING', 'PAID', 'REJECTED', 'VOIDED')),
    CONSTRAINT ck_expense_shares_not_self CHECK (payer_id <> debtor_id),
    CONSTRAINT ck_expense_shares_resolved CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CONSTRAINT ux_expense_shares_debtor   UNIQUE (source_transaction_id, debtor_id)
);

COMMENT ON TABLE  finance.expense_shares IS 'Parte de un gasto compartido que debe otra persona (FIN-16). La deuda abierta entre dos personas es la suma de sus partes PENDING, por moneda.';
COMMENT ON COLUMN finance.expense_shares.source_transaction_id IS 'Gasto de quien pagó. NULL si se borró: la parte pagada se conserva, y anular las pendientes lo hace la aplicación al borrar.';
COMMENT ON COLUMN finance.expense_shares.amount IS 'Lo que se debe, en currency_code, la moneda del gasto de quien pagó.';
COMMENT ON COLUMN finance.expense_shares.description IS 'Copia de la descripción del gasto al compartirlo, para que la parte se pueda mostrar aunque el gasto ya no exista.';
COMMENT ON COLUMN finance.expense_shares.occurred_at IS 'Copia de la fecha del gasto al compartirlo.';
COMMENT ON COLUMN finance.expense_shares.status IS 'PENDING: deuda abierta. PAID: quien debe la aprobó. REJECTED: quien debe la rechazó. VOIDED: se anuló al borrar el gasto.';

CREATE INDEX ix_expense_shares_payer  ON finance.expense_shares (payer_id, status);
CREATE INDEX ix_expense_shares_debtor ON finance.expense_shares (debtor_id, status);

CREATE TRIGGER trg_expense_shares_updated_at
    BEFORE UPDATE ON finance.expense_shares
    FOR EACH ROW EXECUTE FUNCTION finance.set_updated_at();


-- SET NULL y no CASCADE: borrar a un usuario no se lleva los movimientos de otro.
ALTER TABLE finance.transactions
    ADD COLUMN shared_by_user_id UUID REFERENCES finance.users (id)          ON DELETE SET NULL,
    ADD COLUMN share_id          UUID REFERENCES finance.expense_shares (id) ON DELETE SET NULL,
    ALTER COLUMN account_id DROP NOT NULL,
    DROP CONSTRAINT ck_transactions_shape,
    -- Con account_id nullable, destination_account_id <> account_id daría NULL
    -- y el CHECK pasaría: la transferencia exige la cuenta explícitamente.
    ADD CONSTRAINT ck_transactions_shape CHECK (
        (type = 'TRANSFER'
            AND account_id IS NOT NULL
            AND destination_account_id IS NOT NULL
            AND destination_account_id <> account_id
            AND category_id IS NULL)
        OR
        (type <> 'TRANSFER'
            AND destination_account_id IS NULL
            AND (category_id IS NOT NULL OR status = 'PENDING'))
    ),
    ADD CONSTRAINT ck_transactions_account CHECK (account_id IS NOT NULL OR status = 'PENDING');

COMMENT ON COLUMN finance.transactions.status IS 'PENDING: sin efecto hasta aprobarse; no mueve current_balance ni cuenta en reportes, y si no es transferencia puede no tener cuenta ni categoría todavía (FIN-16). CONFIRMED: todo lo demás. Rechazar un pendiente lo borra.';
COMMENT ON COLUMN finance.transactions.shared_by_user_id IS 'Quién compartió el movimiento con el usuario (FIN-16): quien pagó, en la parte de quien debe; quien debe, en el ingreso de quien pagó; quien transfirió, en una transferencia compartida.';
COMMENT ON COLUMN finance.transactions.share_id IS 'Parte compartida de la que sale el movimiento: el gasto de quien debe y el ingreso de quien pagó al aprobarse.';

CREATE INDEX ix_transactions_share
    ON finance.transactions (share_id) WHERE share_id IS NOT NULL;

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. Falla si ya hay pendientes sin cuenta o sin categoría: rechazarlos
-- antes. Las afiliaciones y las partes se pierden.
--
-- BEGIN;
-- DROP INDEX finance.ix_transactions_share;
-- ALTER TABLE finance.transactions
--     DROP CONSTRAINT ck_transactions_account,
--     DROP CONSTRAINT ck_transactions_shape,
--     ADD CONSTRAINT ck_transactions_shape CHECK (
--         (type = 'TRANSFER'
--             AND destination_account_id IS NOT NULL
--             AND destination_account_id <> account_id
--             AND category_id IS NULL)
--         OR
--         (type <> 'TRANSFER'
--             AND destination_account_id IS NULL
--             AND category_id IS NOT NULL)
--     ),
--     ALTER COLUMN account_id SET NOT NULL,
--     DROP COLUMN share_id,
--     DROP COLUMN shared_by_user_id;
-- COMMENT ON COLUMN finance.transactions.status IS 'PENDING: registrado por el asistente y sin efecto hasta aprobarse; no mueve current_balance ni cuenta en reportes. CONFIRMED: todo lo demás. Rechazar un pendiente lo borra.';
-- DROP TABLE finance.expense_shares;
-- DROP TABLE finance.affiliations;
-- COMMIT;
-- -----------------------------------------------------------------------------
