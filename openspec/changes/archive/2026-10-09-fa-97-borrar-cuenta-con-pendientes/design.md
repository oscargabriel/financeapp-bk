# Design

## Context

`DeleteAccountUseCase` lee la cuenta, exige saldo cero y llama a `AccountRepositoryPort.softDelete`,
que hace un `UPDATE ... SET deleted_at = now()` y devuelve si afectó la fila (FA-25).

Un pendiente no tiene efecto: `trg_transactions_sync_balance` solo aplica o revierte filas
`CONFIRMED`, y rechazar un pendiente ya es un `DELETE` de la fila (FA-76). Solo el asistente crea
pendientes, y solo sobre cuentas activas; un PATCH de movimiento rechaza una cuenta borrada. El
único camino a un pendiente sobre una cuenta borrada es borrar la cuenta después de crearlo.

## Goals / Non-Goals

**Goals:**
- Que ningún pendiente quede apuntando a una cuenta borrada, sin cambiar el contrato del `DELETE`.

**Non-Goals:**
- Tocar la aprobación o el rechazo de pendientes.
- Las cuentas desactivadas (FA-102).

## Decisions

### Borrar los pendientes con la cuenta

Lo decidió el usuario el 09-10-2026. Opciones:

- **Borrar los pendientes en el mismo borrado** (elegida). El pendiente no había tenido efecto, y
  sin su cuenta solo se podía rechazar. El usuario no tiene que limpiar nada antes de borrar.
- **409 `RESOURCE_IN_USE` mientras haya pendientes.** Descartada: obliga a aprobar o rechazar uno
  por uno antes de borrar, por algo que todavía no existe en los saldos.
- **Rechazar la aprobación de un pendiente con cuenta borrada.** Descartada: deja en la lista de
  pendientes movimientos que apuntan a una cuenta que el usuario ya no ve, y que solo se pueden
  rechazar.

El precio: lo que registró el asistente sobre esa cuenta se descarta sin aviso. Se acepta porque
borrar la cuenta ya es la decisión explícita del usuario sobre ella.

### Una sola sentencia en el adapter

`softDelete` pasa a ser un `UPDATE` de la cuenta con un `DELETE` de sus pendientes en un CTE
modificador:

```sql
WITH borrada AS (
    UPDATE finance.accounts SET deleted_at = now()
     WHERE id = :id AND user_id = :userId AND deleted_at IS NULL
    RETURNING id)
, pendientes AS (
    DELETE FROM finance.transactions t USING borrada b
     WHERE t.user_id = :userId AND t.status = 'PENDING'
       AND (t.account_id = b.id OR t.destination_account_id = b.id))
SELECT count(*) AS borradas FROM borrada
```

PostgreSQL ejecuta el `DELETE` del CTE aunque la consulta principal no lo lea, y lo hace en la misma
sentencia: o pasan las dos cosas o ninguna. Si la cuenta ya no estaba viva, `borrada` sale vacía y no
se borra ningún pendiente.

Se descartó un método de puerto aparte (`deletePendingOfAccount`) llamado desde el caso de uso: dos
sentencias necesitan un `TransactionalOperator` para no dejar la cuenta borrada con sus pendientes
vivos, y en este proyecto ese operador vive en los adapters (`TransactionR2dbcAdapter`). El
contrato queda escrito en el Javadoc del puerto.

El caso de uso no cambia: el 409 por saldo y el 404 ocurren antes de `softDelete`, así que un
borrado rechazado no elimina pendientes por construcción.

## Risks / Trade-offs

- [La regla vive en SQL y la suite no la ve] → como todo `*R2dbcAdapter`, la verifica `bruno/`
  contra la base real.
- [Se pierde lo que registró el asistente sin aviso] → aceptado con la decisión. Si hiciera falta
  avisar, sería un cuerpo en la respuesta del `DELETE`, que hoy es 204.
