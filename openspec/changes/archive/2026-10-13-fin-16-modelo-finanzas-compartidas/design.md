# Design

## Context

El motivo está en proposal.md (*Why*). Lo que condiciona el modelo:

- Un pendiente se aprueba con un `UPDATE` a `CONFIRMED` y se rechaza con un `DELETE`
  (`modelo-datos.md`, *Movimientos pendientes*). El trigger de saldos ignora los pendientes, así que
  un pendiente sin cuenta no lo afecta: al aprobarse, la fila nueva ya trae la cuenta.
- `ck_transactions_shape` exige categoría en todo gasto o ingreso, y `account_id` es `NOT NULL`.
- El trigger `trg_transactions_usd_equivalent` solo necesita moneda, monto, fecha y la zona del
  usuario. Un pendiente sin cuenta lleva igual su `currency_code`.
- `users` se borra lógicamente, y los scripts de datos lo borran físicamente en cascada
  (`test-data.sql`, `demo-data.sql`).

## Goals / Non-Goals

**Goals:**

- Que FIN-65, FIN-66, FIN-67 y FIN-68 se implementen sin cambiar más el esquema.
- Que la base impida los estados que no dependen de otras filas: partes con sí mismo, montos no
  positivos, estados desconocidos, dos relaciones abiertas entre las mismas personas y un confirmado
  sin cuenta.

**Non-Goals:**

- Hacer cumplir en la base las reglas que dependen de otras filas. Las decide la aplicación, como el
  saldo no negativo del alta en lote.

## Decisions

### 1. Una fila de `affiliations` por solicitud

```sql
CREATE TABLE finance.affiliations (
    id               UUID         PRIMARY KEY,
    requester_id     UUID         NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    addressee_email  VARCHAR(255) NOT NULL,
    addressee_id     UUID                  REFERENCES finance.users (id) ON DELETE CASCADE,
    status           VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED',
    created_at, updated_at,
    CONSTRAINT ck_affiliations_status    CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'UNLINKED')),
    CONSTRAINT ck_affiliations_email     CHECK (addressee_email = lower(addressee_email)),
    CONSTRAINT ck_affiliations_not_self  CHECK (addressee_id <> requester_id),
    -- Sin destinatario nadie la recibe: solo puede seguir abierta o cancelarse.
    CONSTRAINT ck_affiliations_unknown   CHECK (addressee_id IS NOT NULL OR status IN ('REQUESTED', 'CANCELLED'))
);
```

Tras un rechazo, una cancelación o una desvinculación se puede volver a solicitar (FIN-65). Cada
solicitud es una fila nueva, y la vieja conserva su estado final.

- **Descartado: una fila por pareja que se reabre.** Pierde el historial y obliga a reescribir quién
  envió, porque la nueva solicitud puede ir en el sentido contrario.

Las fechas de aceptación o desvinculación no tienen columna propia: ningún criterio del epic las pide,
y `updated_at` dice cuándo fue el último cambio.

### 2. El correo no registrado se guarda y no se resuelve después

`addressee_email` se guarda siempre, ya normalizado (minúsculas, sin espacios, como el registro).
`addressee_id` solo se llena si el correo tenía cuenta al enviar la solicitud. Si la persona se
registra después, la solicitud vieja no le llega: según el epic, «nadie la recibe». La pantalla de
afiliados del remitente muestra el correo, y el nombre cuando hay usuario.

- **Descartado: tabla aparte de invitaciones.** Partiría en dos el listado del remitente y la regla de
  unicidad, para un caso en que no pasa nada más que cancelar.
- **Descartado: resolver la solicitud al registrarse.** Contradice al epic, y haría que el alta
  dependiera de las solicitudes pendientes.

### 3. Unicidad de lo abierto con dos índices parciales

```sql
CREATE UNIQUE INDEX ux_affiliations_open_pair
    ON finance.affiliations (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
 WHERE status IN ('REQUESTED', 'ACCEPTED') AND addressee_id IS NOT NULL;

CREATE UNIQUE INDEX ux_affiliations_open_email
    ON finance.affiliations (requester_id, addressee_email)
 WHERE status = 'REQUESTED';
```

El primero impide dos relaciones abiertas entre dos usuarios en cualquier sentido. Así una carrera
A→B contra B→A también la frena la base. El `addressee_id IS NOT NULL` es necesario: `LEAST` y
`GREATEST` ignoran los `NULL`, y sin ese filtro dos solicitudes de A a correos no registrados
distintos colisionarían como (A, A). El segundo índice impide repetir una solicitud abierta al mismo
correo no registrado.

Hay dos índices de lectura más: por `requester_id` y por `addressee_id`.

### 4. Las partes en `expense_shares`, con copia de lo que muestran

```sql
CREATE TABLE finance.expense_shares (
    id                     UUID           PRIMARY KEY,
    source_transaction_id  UUID           REFERENCES finance.transactions (id) ON DELETE SET NULL,
    payer_id               UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    debtor_id              UUID           NOT NULL REFERENCES finance.users (id) ON DELETE CASCADE,
    amount                 NUMERIC(18,4)  NOT NULL,
    currency_code          CHAR(3)        NOT NULL REFERENCES finance.currencies (code) ON DELETE RESTRICT,
    description            VARCHAR(255)   NOT NULL,
    occurred_at            TIMESTAMPTZ    NOT NULL,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    resolved_at            TIMESTAMPTZ,
    created_at, updated_at,
    CONSTRAINT ck_expense_shares_amount   CHECK (amount > 0),
    CONSTRAINT ck_expense_shares_status   CHECK (status IN ('PENDING', 'PAID', 'REJECTED', 'VOIDED')),
    CONSTRAINT ck_expense_shares_not_self CHECK (payer_id <> debtor_id),
    CONSTRAINT ck_expense_shares_resolved CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CONSTRAINT ux_expense_shares_debtor   UNIQUE (source_transaction_id, debtor_id)
);
```

- `amount` y `currency_code` están en la moneda del gasto de quien pagó: es lo que se debe. Si quien
  debe aprueba con una cuenta en otra moneda, FIN-66 convierte su movimiento, no la parte.
- `description` y `occurred_at` son copia del gasto. La parte pagada sobrevive al borrado del gasto
  (decisión del epic), y FIN-67 la lista con descripción y fecha. Mientras haya partes pendientes, el
  gasto no se edita, así que la copia no se desactualiza cuando importa.
- `source_transaction_id` pasa a `NULL` al borrar el gasto. Que las pendientes queden anuladas
  (`VOIDED`) lo hace la aplicación en la misma transacción. Es un `UPDATE` de estado, no una acción
  de la FK.
- Deuda abierta: `SUM(amount) WHERE status = 'PENDING'`, agrupada por moneda (FIN-67). Hay índices
  por `(payer_id, status)` y `(debtor_id, status)`.
- `ux_expense_shares_debtor` impide dos partes de la misma persona en un gasto. Las partes de gastos
  ya borrados (`NULL`) no colisionan entre sí.

- **Descartado: que la parte sea solo el gasto pendiente de quien debe.** Rechazar ese pendiente es
  un `DELETE`, y quien pagó tiene que seguir viendo la parte como rechazada. Además, los estados
  pagada y anulada no tienen dónde vivir en `transactions`.
- **Descartado: porcentajes.** El epic fija montos.

### 5. El movimiento apunta a la parte y a quien lo compartió

Columnas nuevas en `transactions`:

```sql
shared_by_user_id  UUID  REFERENCES finance.users (id)          ON DELETE SET NULL,
share_id           UUID  REFERENCES finance.expense_shares (id) ON DELETE SET NULL
```

| Movimiento | `user_id` | `shared_by_user_id` | `share_id` |
|---|---|---|---|
| Gasto de quien pagó (el total) | quien pagó | — | — |
| Gasto pendiente de quien debe | quien debe | quien pagó | la parte |
| Ingreso pendiente de quien pagó, al aprobarse la parte | quien pagó | quien debe | la parte |
| Ingreso pendiente del destinatario de una transferencia compartida | destinatario | quien transfirió | — |

`shared_by_user_id` es lo que filtra FIN-68, y cubre también la transferencia, que no tiene parte.
`share_id` lleva de un pendiente a su parte para aprobarla o rechazarla, y de la parte al gasto de
origen (`source_transaction_id`) para mostrar el total. Hay índice parcial por `share_id`.

- **Descartado: que la parte guarde los ids de los dos movimientos.** El filtro por persona tendría
  que unir con las partes, y el ingreso de una transferencia compartida no tendría de dónde sacar
  quién la compartió.
- **Descartado: derivar quién compartió del movimiento de origen.** El origen se puede borrar con la
  parte ya pagada, y el movimiento de quien debe perdería el dato.

`ON DELETE SET NULL` en las dos columnas, y no `CASCADE`: borrar a un usuario no puede llevarse los
movimientos de otro. No se agrega un `CHECK` que ate `share_id` a `shared_by_user_id`: en el borrado
en cascada de un usuario, las dos acciones `SET NULL` corren en orden indefinido, y un `CHECK` no
diferible fallaría entre una y otra.

### 6. Pendiente sin cuenta ni categoría, para cualquier origen

```sql
ALTER COLUMN account_id DROP NOT NULL;

CONSTRAINT ck_transactions_shape CHECK (
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
CONSTRAINT ck_transactions_account CHECK (account_id IS NOT NULL OR status = 'PENDING')
```

La transferencia exige `account_id IS NOT NULL` de forma explícita: con la columna nullable,
`destination_account_id <> account_id` daría `NULL`, y un `CHECK` con `NULL` pasa.

La holgura vale para cualquier pendiente, no solo para los compartidos. FIN-58 desliga el pendiente
de su origen, y el correo (FIN-12) también va a crear pendientes que quizá no sepan la cuenta.

- **Descartado: limitarla a `shared_by_user_id IS NOT NULL`.** Ataría el pendiente al origen, justo
  lo que FIN-58 quita, y obligaría a otro update cuando el asistente o el correo lo necesiten.

El trigger de saldos no cambia: un `PENDING` no lo toca, y al aprobarse la fila nueva trae la cuenta.
Un pendiente sin cuenta tampoco cuenta en `scheduled_balance_delta`, `committed_credit`,
`check-saldos.sql` ni las vistas, que filtran `CONFIRMED`.

### 7. Lo que queda en la aplicación

Lo hacen cumplir FIN-65 y FIN-66, en el caso de uso:

- que la suma de las partes no supere el total;
- que solo se comparta con un afiliado vigente;
- que un gasto con partes pendientes no cambie de monto ni de partes;
- las transiciones de estado válidas.

Son reglas que dependen de otras filas. Este repo las decide en la aplicación, como el saldo no
negativo del alta en lote, y un trigger las escondería de los tests.

## Risks / Trade-offs

- **Código que asume que un pendiente trae cuenta.** El mapeo de `transactions` en los adapters de
  `core` e `ia` falla con una fila sin cuenta.
  → Mitigación: hoy no existe ninguna fila así. FIN-66 crea la primera, y su change tiene que cubrir
  la lista de pendientes, la aprobación y el rechazo con esa fila. Queda dicho en `modelo-datos.md`.
- **Copia desactualizada.** La descripción o la fecha de una parte pagada no siguen al gasto si este
  se edita después.
  → Mitigación: es intencional, porque la parte pagada registra lo que se pagó.
- **Correo no registrado que después se registra y no ve la solicitud.**
  → Mitigación: es la decisión del epic. El remitente la cancela y vuelve a enviar.

## Migration Plan

`docs/database/update/20261013_01_finanzas_compartidas.sql`, en una transacción:

1. Crea `affiliations` y `expense_shares`.
2. Agrega las dos columnas a `transactions`.
3. Quita el `NOT NULL` de `account_id`.
4. Reemplaza `ck_transactions_shape` y agrega `ck_transactions_account`.

Como no hay filas sin cuenta, los `CHECK` nuevos validan todo lo existente.

- **Orden en Neon:** indiferente respecto a la app. Ninguna versión desplegada nombra lo nuevo.
- **En local:** reinicia los datos (`cargar-datos-local.sql`).
- **Reversión** (bloque comentado al final del update): borra las columnas y las tablas, y restaura el
  `NOT NULL` y el `CHECK` viejo. Falla si ya hay pendientes sin cuenta o sin categoría; en ese caso
  hay que rechazarlos antes.
