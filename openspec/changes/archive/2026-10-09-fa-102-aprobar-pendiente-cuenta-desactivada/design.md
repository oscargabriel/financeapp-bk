# Design

## Context

`ApprovePendingTransactionUseCase` lee el pendiente con `PendienteDelUsuario.buscar` (404 y 409 por
estado) y llama a `TransactionRepositoryPort.confirm`, un `UPDATE ... SET status = 'CONFIRMED'`
condicionado a `status = 'PENDING'`. `trg_transactions_sync_balance` mueve los saldos al ver ese
paso y no mira `is_active`.

`AccountRepositoryPort.findActiveByIdAndUser` devuelve la cuenta no borrada del usuario, activa o no
(el nombre es anterior a FA-68). Un pendiente no puede apuntar a una cuenta borrada: FA-97 los borra
con ella.

## Goals / Non-Goals

**Goals:**
- Que aprobar sea tan estricto con las cuentas desactivadas como el alta, sin tocar el rechazo.

**Non-Goals:**
- Revalidar al aprobar las demás reglas del alta (moneda COP, categoría compatible). El pendiente ya
  pasó por ellas al crearse, y las cuentas no cambian de moneda con movimientos (FA-24).

## Decisions

### 409 `INVALID_STATE` sobre la cuenta

Lo decidió el usuario el 09-10-2026. Opciones:

- **Rechazar con 409 `INVALID_STATE`** (elegida). Aprobar no lleva cuerpo: el problema no está en lo
  que el cliente mandó, sino en el estado de algo que ya existe. Es el mismo status y code que aprobar
  un movimiento ya confirmado. El `field` apunta a la cuenta para que el cliente sepa cuál reactivar.
- **Rechazar con 400 `VALIDATION_ERROR`**, como el alta. Descartada: un 400 sobre un campo que el
  cliente no envió confunde, y el cliente no puede corregir el request para que pase.
- **Permitir la aprobación** y documentarlo. Descartada: aprobar sería la única puerta para mover el
  saldo de una cuenta que el alta y el PATCH consideran cerrada.
- **Borrar los pendientes al desactivar**, como FA-97. Descartada: desactivar se deshace y borrar no;
  al reactivar, lo que registró el asistente ya no estaría.

### Un error por cuenta desactivada

Si las dos lo están, salen los dos errores, igual que el alta reporta todos los errores de un
elemento. El cliente reactiva una cuenta y el siguiente intento ya no se lo vuelve a decir.

### La regla en el caso de uso, no en el `UPDATE`

Después de `PendienteDelUsuario.buscar`, el caso de uso lee la cuenta origen y, si es transferencia,
la destino, con `findActiveByIdAndUser`. Si alguna está desactivada, falla con el 409 sin llamar a
`confirm`. Para leer las cuentas se agrega `AccountRepositoryPort` al caso de uso. Así la regla se
prueba en la suite con mocks.

Se descartó condicionar `CONFIRMAR` con un `EXISTS` sobre las cuentas activas: cero filas no
distingue "otro request lo aprobó" (404) de "la cuenta está desactivada" (409), y la regla quedaría
donde solo `bruno/` la ve.

Que `findActiveByIdAndUser` salga vacío solo pasaría con una cuenta borrada, que ya no puede tener
pendientes (FA-97). No se le inventa un error: el vacío sigue de largo hacia `confirm`.

## Risks / Trade-offs

- [Carrera: la cuenta se desactiva entre la lectura y el `UPDATE`] → el pendiente se aprueba. Es la
  misma ventana que tiene el alta, que también lee las cuentas antes de insertar. Se acepta.
- [Una o dos lecturas más por aprobación] → son por clave primaria, sobre una operación manual.
