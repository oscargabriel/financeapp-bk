# Design

## Context

`finance.accounts` ya tiene `deleted_at`, y todo lo que lee cuentas vivas lo filtra: el listado (con
o sin `includeInactive`), el `SELECT` del PATCH (`findActiveByIdAndUser`, FA-24) y las referencias
contra las que se validan los movimientos (`AccountQueryPort.findByUser(userId, true)`). El índice
único del nombre es parcial (`WHERE deleted_at IS NULL`). Marcar la columna basta para sacar la
cuenta del listado, liberar el nombre e impedir que se use en un movimiento: no hay que tocar
ninguna de esas consultas.

El reporte de movimientos no hace `JOIN` con `accounts`: devuelve `account_id` de la fila y filtra
por él. Las vistas de gasto mensual tampoco leen cuentas. La historia no cambia sin tocar nada.

El PATCH de movimientos conserva la cuenta guardada sin revalidarla mientras el parche no traiga
`accountId` ni `destinationAccountId` (`UpdateTransactionUseCase`).

## Goals / Non-Goals

**Goals:**
- Borrar sin romper ninguna lectura de historia ni ningún movimiento existente.
- Que ningún saldo desaparezca del listado sin que el usuario lo haya llevado a cero.

**Non-Goals:**
- Tocar el esquema, las vistas o las consultas de reportes.
- `is_active`: el borrado no lo cambia. Desactivar y reactivar es FA-68.

## Decisions

### Leer, decidir y marcar

`DeleteAccountUseCase`:

1. `findActiveByIdAndUser` (el de FA-24, que no filtra `is_active`). Vacío → 404 `NOT_FOUND` en
   `id`, el mismo `UpdateAccountUseCase.noEncontrada()`.
2. `currentBalance` distinto de cero → 409 `RESOURCE_IN_USE` en `currentBalance`.
3. `softDelete`: `UPDATE finance.accounts SET deleted_at = now() WHERE id AND user_id AND
   deleted_at IS NULL`. Cero filas (borrada entre los dos pasos) → 404.

Se descartó el `UPDATE` único de FA-21 sin lectura previa: allí el borrado no dependía de nada de
la fila, aquí depende del saldo. Se descartó también meter `AND current_balance = 0` en el `UPDATE`
y responder con lo que devuelva: cero filas sería ambiguo entre 404 y 409, y desempatarlo exigiría
la misma lectura.

### 409 `RESOURCE_IN_USE` sobre `currentBalance`

El saldo es lo que impide el borrado, y es un campo que el cliente ve en la respuesta de la
cuenta: señalarlo le dice qué tiene que llevar a cero. `RESOURCE_IN_USE` se definió en FA-20 para
"un cambio que dejaría inconsistentes otros datos"; aquí el dato que quedaría fuera de la vista es
el dinero o la deuda de la cuenta. Se descartó un código nuevo (`NON_ZERO_BALANCE`): el cliente
decide igual con el `field`, y un código por cada causa de 409 fragmentaría el enum.

### Se borra aunque tenga movimientos

Igual que en categorías (FA-21): para eso es el borrado lógico, y la FK diferida de `transactions`
ya impide el borrado físico. Obligar a eliminar los movimientos sería reescribir la historia.

### PATCH y DELETE de movimientos viejos siguen permitidos

Un PATCH que cambie el monto, o un DELETE, de un movimiento de la cuenta borrada mueve su
`current_balance` por el trigger, y la cuenta queda borrada con saldo distinto de cero. Se acepta:
la cuenta no se ve en ningún lado, la contrapartida de una transferencia en una cuenta viva sí se
mueve como debe, y bloquearlo exigiría revalidar la cuenta guardada en el PATCH y en el DELETE de
movimientos, que es otro cambio con su propia spec. Queda en "Fuera de alcance".

### 204 sin cuerpo

Igual que `DELETE /api/categories/{id}` y `DELETE /api/transactions/{id}`.

### Bruno: al final de `bruno/accounts/`

Después del seq 40, con el usuario de `tokenCuentas`: una cuenta nueva `Caja chica` en 0, un gasto
que la deja en -30000, el 409, el PATCH de FA-24 que la devuelve a 0 y el 204. Así se prueba la
regla del saldo con el camino que el usuario tiene para cumplirla. Los 409 y 404 sobre cuentas del
escenario (`Nequi`, `Efectivo`) no escriben, así que el escenario no cambia.

## Risks / Trade-offs

- [Carrera entre la lectura y el `UPDATE`] → Un movimiento registrado en medio dejaría borrada una
  cuenta con saldo. Se acepta, el mismo supuesto de FA-20, FA-24 y FA-62: un usuario por cuenta,
  sin escritores concurrentes en la práctica.
- [Saldo de una cuenta borrada que deja de ser cero] → Ver arriba; no se ve en el API.

## Migration Plan

Sin cambios de esquema: el despliegue es solo la imagen nueva.
