# Proposal

Origen: FA-133. Pedido desde la raíz de coordinación el 11-10-2026 con el texto «las cuentas de
devito no pueden bajar de 0 mostrar un mensaje de error». El usuario aclaró después que la regla
aplica a todos los tipos menos `CREDIT`. Lo espera FA-142 (front), que va a mostrar el rechazo en el
formulario.

## Why

Hoy nada impide que una cuenta de efectivo, débito, ahorros, inversión u otra quede por debajo de
cero. El alta acepta un `initialBalance` negativo, y el alta en lote, el `PATCH`, el `DELETE` y la
aprobación de pendientes mueven el saldo sin tope. Solo en una tarjeta de crédito un saldo negativo
tiene sentido, porque representa la deuda. En las demás cuentas es un dato imposible, y el front
necesita un rechazo que pueda mostrar en el campo que lo causó.

## What Changes

- Regla nueva: **ninguna petición puede bajar el saldo vigente de una cuenta que no es `CREDIT` por
  debajo de cero**. Se mide sobre el saldo vigente (`currentBalance`), y solo una operación que lo
  baja puede rechazarse por esta regla. Una cuenta que ya está en negativo sigue recibiendo dinero
  aunque no alcance a salir de cero.
- Error nuevo: 409 `INSUFFICIENT_FUNDS`, uno por cuenta afectada. Su `description` nombra la cuenta y
  el saldo en el que quedaría, y se puede mostrar tal cual. El `field` es:
  - `amount` en `PATCH /api/transactions/{id}` y en la aprobación;
  - `[i].amount` en el alta en lote;
  - `id` en `DELETE /api/transactions/{id}`;
  - `initialBalance` en `PATCH /api/accounts/{id}`.
- `POST /api/transactions`: el lote se rechaza entero si deja alguna cuenta no crédito por debajo de
  cero. Cuenta el efecto neto del lote, así que un ingreso del mismo lote compensa un gasto. El
  error va en el primer elemento que saca dinero de esa cuenta y la deja en negativo, en el orden
  del lote.
- `PATCH /api/transactions/{id}`: se rechaza si el movimiento resultante baja alguna cuenta por
  debajo de cero. Eso incluye subir un gasto, cambiar de cuenta o de tipo, reducir un ingreso o una
  transferencia entrante, y pasar un ingreso a fecha futura.
- `DELETE /api/transactions/{id}`: se rechaza si borrar un ingreso o una transferencia entrante deja
  la cuenta en negativo. Antes era un `DELETE` sin lectura previa; ahora lee el movimiento antes de
  borrarlo.
- `POST /api/transactions/{id}/approve`: se rechaza si aprobar deja la cuenta en negativo, y el
  movimiento sigue pendiente.
- Cuentas: una cuenta no `CREDIT` no se crea ni se corrige con `initialBalance` negativo (400
  `VALIDATION_ERROR` en `initialBalance`). Bajar el saldo inicial hasta dejar el vigente en negativo
  es 409 `INSUFFICIENT_FUNDS`.
- **No se validan**, y quedan documentados así:
  - los movimientos programados (fecha futura) al crearse;
  - lo programado cuando llega su fecha;
  - las ocurrencias que `SeriesAlDia` crea al leer.

  Lo que llega solo puede dejar una cuenta en negativo. Desde ahí, la cuenta no acepta más
  salidas hasta recibir dinero.
- Advertencia escrita en contrato y spec: el alta de una serie con inicio pasado y el
  `PATCH /api/recurrences/{id}` con `scope: ALL` pueden dejar una cuenta en negativo. Se permiten
  por decisión del usuario, y FA-143 queda para analizarlo.
- `contrato-api.md` y la colección `bruno/` al día.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: la regla general del saldo no negativo, el saldo inicial negativo según el tipo y las
  cuentas que ya están en negativo.
- `transacciones`: el rechazo en el alta en lote, el `PATCH`, el `DELETE` y la aprobación; los
  programados no se validan.
- `recurrentes`: la advertencia de que el alta y la edición con `ALL` no validan el saldo.

## Impact

- Código:
  - Casos de uso `CreateTransactionsUseCase`, `UpdateTransactionUseCase`,
    `DeleteTransactionUseCase`, `ApprovePendingTransactionUseCase` y `UpdateAccountUseCase`.
  - Una clase nueva en `application/usecase` que calcula el efecto de un movimiento sobre el saldo
    vigente.
  - Una constraint de clase sobre `CreateAccountRequest`.
  - El código nuevo `INSUFFICIENT_FUNDS` en `ErrorCodes`.
- Base de datos: sin cambios de esquema ni datos. La regla vive en la aplicación (ver `design.md`).
- API: un 409 nuevo en seis rutas y un 400 nuevo en las dos de cuentas. No cambian rutas, cuerpos
  ni autenticación, así que `bruno-personal/` no necesita requests nuevos.
- Front (FA-142): tiene que ubicar en su campo un 409 con `field`, además de los 400.
- Memoria `contrato-alta-lote`: el lote ya no solo falla con 400. Se revisa al archivar.

## Fuera de alcance

- Validar el saldo en `POST /api/recurrences` y en `PATCH /api/recurrences/{id}` con `scope: ALL`
  → FA-143 (Backlog).
- Las compras en cuotas: solo existen sobre tarjetas `CREDIT`.
- Corregir las cuentas que ya están en negativo en la base local o en Neon. No se migra ningún dato.
- Una restricción en la base (CHECK o trigger) que respalde la regla. Se descarta en `design.md`.
