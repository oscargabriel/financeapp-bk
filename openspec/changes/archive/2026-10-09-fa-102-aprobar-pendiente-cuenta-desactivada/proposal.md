# Proposal

Origen: FA-102, del tablero (Bug). Surgió al proponer FA-68, que permite desactivar una cuenta por el
PATCH con cualquier saldo. Es el caso hermano de FA-97 (cuenta **borrada** con pendientes).

## Why

`POST /api/transactions/{id}/approve` confirma el pendiente sin mirar sus cuentas, y el trigger mueve
el saldo de una cuenta desactivada. El alta y el PATCH de movimientos rechazan esa misma cuenta, así
que aprobar es la única puerta para mover una cuenta desactivada. Desde FA-77 el asistente crea
pendientes, y desde FA-68 cualquiera puede desactivar una cuenta que los tenga.

## What Changes

- Aprobar un pendiente cuya cuenta origen o destino está desactivada responde **409
  `INVALID_STATE`**, con un error por cada cuenta desactivada: en `accountId` si es el origen y en
  `destinationAccountId` si es el destino. El pendiente sigue pendiente y ningún saldo cambia.
- Rechazar el pendiente sigue funcionando con la cuenta desactivada: es, con reactivar la cuenta, la
  salida que tiene el usuario.
- Reactivada la cuenta, el pendiente se aprueba como cualquier otro.

Decidido por el usuario el 09-10-2026, antes de proponer, entre cuatro opciones: rechazar con 409
(elegida), rechazar con 400 como el alta, permitir la aprobación, y borrar los pendientes al
desactivar, como FA-97. El porqué está en `design.md`.

## Fuera de alcance

- El PATCH de un pendiente sobre una cuenta desactivada: ya lo rechaza `ReferenciasDelUsuario`
  cuando el parche toca la cuenta, y si no la toca no mueve saldos.
- Avisar en `GET /api/transactions/pending` que un pendiente no se puede aprobar: la lista no cambia.
- Ningún cambio de esquema ni de `ErrorCodes`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: un requisito nuevo, aprobar un pendiente con una cuenta desactivada.

## Impact

- API: `POST /api/transactions/{id}/approve` tiene un 409 nuevo, con `field` en la cuenta. Mismo
  contrato en el camino feliz.
- Código: `ApprovePendingTransactionUseCase` lee las cuentas del pendiente con
  `AccountRepositoryPort.findActiveByIdAndUser` antes de confirmar. Sin cambios en adapters.
- Bruno: en `bruno/pending/`, desactivar las dos cuentas del usuario `pendientes@`, intentar aprobar,
  rechazar con la cuenta desactivada y reactivar, antes de las aprobaciones que ya existen.
- Front (`financeapp-fr`): al aprobar puede recibir este 409, y lo muestra como cualquier otro error.
