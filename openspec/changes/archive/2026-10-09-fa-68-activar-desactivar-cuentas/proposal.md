# Proposal

Origen: FA-68, del tablero. Surgió al tomar FA-25 (05-10-2026), que hizo del `DELETE` de una cuenta un
borrado lógico y dejó `is_active` sin forma de cambiarse.

## Why

`is_active` ya tiene efecto: `GET /api/accounts` oculta las cuentas desactivadas salvo con
`includeInactive=true`, `GET /api/reports/balance` no las trae y los movimientos nuevos las rechazan.
Pero ningún endpoint la cambia: el PATCH de FA-24 rechaza `isActive` con 400. El usuario no puede
retirar una cuenta que dejó de usar sin borrarla, y el borrado exige saldo cero y no se deshace.

## What Changes

- `PATCH /api/accounts/{id}` admite `isActive` (`true` o `false`) junto con los demás campos, o solo.
  Con eso se desactiva y se reactiva una cuenta propia. **BREAKING** para un cliente que contara con
  el 400 en `isActive`: ese campo deja de ser un error.
- Desactivar se permite con cualquier saldo, a favor o en deuda. El borrado sigue exigiendo saldo
  cero.
- Pedir el estado que la cuenta ya tiene no es un error: 200 con la cuenta sin cambios.
- Un parche que solo trae `isActive` cuenta como parche con cambios; `{"isActive": null}` sigue
  siendo un parche vacío.
- `currentBalance` y `type` siguen rechazados con 400 en el parche.

Decidido conversando antes de proponer (09-10-2026):

- **Endpoint**: `isActive` en el PATCH existente, en vez de dos acciones `POST .../deactivate` y
  `.../activate` o un `PUT .../active`. El porqué está en `design.md`.
- **Saldo**: se desactiva con cualquier saldo.
- **Estado repetido**: 200 idempotente, no 409.

## Fuera de alcance

- **Aprobar un pendiente sobre una cuenta desactivada.** `POST /api/transactions/{id}/approve`
  confirma el movimiento sin revisar el estado de su cuenta, así que mueve el saldo de una
  desactivada. Queda en FA-102, hermana de FA-97; no cambia nada de lo que este change toca.
- El asistente (`/api/assistant`) ya solo ofrece cuentas activas al modelo; no cambia.
- `GET /api/reports/balance` sigue sin traer las desactivadas, tengan o no saldo. Mostrar el saldo
  retirado en otra parte sería una decisión de reportes, no de esta tarea.
- Ningún cambio de esquema: `is_active` ya existe en `finance.accounts`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: el parche admite `isActive`; se agregan las reglas de desactivar y reactivar (saldo,
  estado repetido, efecto sobre el listado y sobre los movimientos nuevos), y `isActive` sale de los
  campos que el parche rechaza.

## Impact

- API: `PATCH /api/accounts/{id}` acepta un campo más. Ninguna ruta nueva.
- Código: `UpdateAccountRequest`, `UpdateAccountCommand`, `UpdateAccountUseCase` y el `UPDATE` de
  `AccountR2dbcAdapter`. Sin cambios en seguridad, errores ni esquema.
- Bruno: `bruno/accounts/modificar-cuenta-campos-del-sistema.yml` deja de esperar el error en
  `isActive`, y se agregan los requests del ciclo desactivar → rechazo del gasto → reactivar → gasto
  aceptado. Se replican en `bruno-personal/`.
- Front (`financeapp-fr`): puede usar el mismo PATCH para el botón de desactivar.
