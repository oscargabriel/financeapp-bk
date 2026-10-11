# Design

## Context

`current_balance` lo escriben solo dos triggers: `trg_accounts_seed_balance` lo copia de
`initial_balance` al insertar, y `trg_transactions_sync_balance` le suma o resta el efecto de cada
movimiento. El invariante implícito es `current_balance = initial_balance + Σ efecto de los
movimientos`, y el comentario de la columna dice que la aplicación nunca la escribe. Un `UPDATE` de
`initial_balance` sin más lo rompe, **aunque la cuenta no tenga movimientos**: el saldo vigente se
quedaría con el inicial viejo.

El alta de movimientos rechaza una cuenta que no esté en COP, y los movimientos guardan su propio
`currency_code`. Cambiar la moneda de una cuenta con movimientos dejaría un historial en una moneda
y la cuenta en otra.

El PATCH de categorías (FA-20) dejó el patrón a imitar: `null` es "no cambia", parche vacío 400 en
`body` desde el controlador, id parseado a mano, lectura de la fila viva, chequeos, `UPDATE ...
RETURNING` y 404 sin distinguir ajena, borrada e inexistente. `RESOURCE_IN_USE` ya existe.

## Goals / Non-Goals

**Goals:**
- Que el invariante del saldo vigente lo siga manteniendo la base, no la aplicación.
- Que el cliente vea en la respuesta todos los campos que puede editar.

**Non-Goals:**
- Recalcular `current_balance` desde los movimientos: el trigger lo desplaza por diferencia.
- Bloquear a nivel de base el cambio de moneda con movimientos.

## Decisions

### Un trigger corre el saldo vigente, no el `UPDATE` de la aplicación

`trg_accounts_shift_balance`, `BEFORE UPDATE OF initial_balance` con `WHEN (NEW.initial_balance IS
DISTINCT FROM OLD.initial_balance)`, hace `NEW.current_balance := NEW.current_balance +
(NEW.initial_balance - OLD.initial_balance)`. Suma sobre `NEW` y no sobre `OLD` para no pisar otro
cambio de la misma sentencia.

Se descartó `SET current_balance = current_balance + (:nuevo - initial_balance)` en el `UPDATE` del
adapter: funciona igual, pero contradice el comentario de la columna y deja el invariante a merced
de cualquier `UPDATE` futuro, o de un psql a mano, que olvide la segunda columna. Con el trigger,
corregir el saldo inicial desde psql también queda bien.

El trigger va en `schema.sql` y como update `20261005_02_cuentas_saldo_inicial_editable.sql`, con su
reversión comentada al final como el update anterior. Al emitirlo se corre la comparación de
esquemas de `docs/database/modelo-datos.md`.

### La moneda se cambia solo sin movimientos, en las dos direcciones de una transferencia

"Tiene movimientos" es `EXISTS` sobre `finance.transactions` con `account_id = :id OR
destination_account_id = :id`: una cuenta que solo recibió transferencias también tiene historial en
su moneda. La consulta solo se hace si la moneda normalizada difiere de la guardada, y después de
comprobar que la moneda nueva exista y esté activa (`CurrencyQueryPort.exists`, el mismo del alta).

Se descartó convertir el historial a la moneda nueva: necesitaría una tasa por movimiento y es otra
tarea, si llega a hacer falta.

### Los campos de crédito se validan contra el tipo guardado, en el caso de uso

En el alta, `@CamposDeCredito` decide mirando `type` en el mismo cuerpo. En el parche `type` no
viene: el tipo es el de la fila guardada, así que la regla "solo `CREDIT` tiene cupo y días" la
aplica el caso de uso, con un error por campo en un solo `BadRequestException` de varios
`ErrorDetail`. Los rangos (cupo mayor que cero y que quepa en `NUMERIC(18,4)`, días entre 1 y 31) no
dependen del tipo y van en `UpdateAccountRequest`, como dice AGENTS.md.

### `currentBalance`, `type` e `isActive` se leen para rechazarlos

`UpdateAccountRequest` los declara con `@Null`, como `currentBalance` en el alta. Ignorarlos haría
creer al cliente que los cambió. `isActive` es `Boolean`.

### Orden de los chequeos

1. Formato y campos prohibidos: `UpdateAccountRequest` (400).
2. Parche vacío: el controlador (400 en `body`).
3. Cuenta viva por id y usuario (`findActiveByIdAndUser`, sin filtrar `is_active`): 404.
4. Campos de crédito contra el tipo guardado: 400.
5. Si la moneda cambia: que exista (400) y que no haya movimientos (409).
6. `update` con `UPDATE ... WHERE id AND user_id AND deleted_at IS NULL RETURNING`: el choque del
   índice `ux_accounts_user_name` sale como 409 en `name`, y cero filas como 404.

Los 400 que no necesitan la base van antes que los que sí, y ningún paso escribe antes del último.

### La respuesta gana cuatro campos

`Account` suma `initialBalance`, `statementDay` y `paymentDueDay` (ya tenía `creditLimit`), y
`AccountResponse` los publica con `creditLimit`. Se descartó un `GET /accounts/{id}` aparte: no lo
pide nadie, y el listado es lo que el cliente ya consulta. Es aditivo para `GET` y `POST`.

### Davivienda con id fijo en `test-data.sql`

Para el 404 de una cuenta borrada, Bruno necesita su id. Se le da
`20000000-0000-7000-8000-000000000006` con un `\set`, como las demás cuentas del escenario.

## Risks / Trade-offs

- [Carrera entre el `EXISTS` de movimientos y el `UPDATE`] → Un movimiento registrado entre los dos
  pasos dejaría la moneda cambiada con historial. Se acepta, el mismo supuesto de FA-20 y FA-62: un
  usuario por cuenta, sin escritores concurrentes en la práctica.
- [Saldo vigente fuera de `NUMERIC(18,4)`] → El trigger suma una diferencia acotada por
  `@MontoNumeric`; desbordar exigiría saldos cercanos a 10¹⁴. No se valida.

## Migration Plan

1. Aplicar `20261005_02_cuentas_saldo_inicial_editable.sql` en Neon **antes** de desplegar la app:
   con la app nueva y la base vieja, cambiar el saldo inicial descuadraría el vigente. Con la base
   nueva y la app vieja no pasa nada, porque nadie actualiza `initial_balance`.
2. Merge a `main` y despliegue por el pipeline.

Reversión: revertir el commit en `main`, y después correr la reversión comentada del update.
