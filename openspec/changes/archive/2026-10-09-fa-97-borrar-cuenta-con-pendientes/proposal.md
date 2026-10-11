# Proposal

Origen: FA-97, del tablero (Bug). Surgió al proponer FA-76, que agregó el estado de aprobación a los
movimientos.

## Why

`DELETE /api/accounts/{id}` solo exige que el saldo vigente esté en cero, y un movimiento `PENDING`
no mueve el saldo. Una cuenta en cero con pendientes se puede borrar. Si después se aprueba uno, la
cuenta borrada queda con saldo distinto de cero, invisible en `GET /api/accounts`. Desde FA-77 el
asistente crea pendientes, así que el caso ya es alcanzable.

## What Changes

- Borrar una cuenta elimina, en la misma operación, los movimientos `PENDING` del usuario que la
  tienen como origen o destino. No tenían efecto en saldos ni reportes, así que ningún saldo cambia.
- Los pendientes de otras cuentas no se tocan, aunque sean del mismo usuario.
- Un borrado rechazado (409 por saldo, 404) no elimina ningún pendiente.
- Los movimientos confirmados de la cuenta borrada siguen como estaban.

Decidido conversando antes de proponer (09-10-2026), entre las tres opciones de la tarea y una
tercera: **borrar los pendientes con la cuenta**. Se descartaron bloquear el borrado con 409
mientras haya pendientes y rechazar la aprobación de un pendiente cuya cuenta está borrada. El
porqué está en `design.md`.

## Fuera de alcance

- **FA-102**: aprobar un pendiente sobre una cuenta **desactivada**. Desactivar no borra pendientes;
  esa tarea sigue abierta y su decisión no depende de esta.
- Avisar al cliente cuántos pendientes se eliminaron: el `DELETE` sigue respondiendo 204 sin cuerpo.
- Ningún cambio de esquema.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: el borrado lógico elimina los pendientes de la cuenta; "Movimientos de una cuenta
  borrada" pasa a hablar de los confirmados.

## Impact

- API: `DELETE /api/accounts/{id}` tiene un efecto más; mismo contrato de respuesta.
- Código: la sentencia de borrado de `AccountR2dbcAdapter` y el Javadoc de
  `AccountRepositoryPort.softDelete`. El caso de uso no cambia.
- Bruno: en `bruno/accounts/`, pendientes creados por el asistente (stub) sobre `Caja chica` y sobre
  `Bolsillo`, y la lista de pendientes antes y después del borrado.
- Front (`financeapp-fr`): si muestra pendientes, los de una cuenta recién borrada desaparecen de la
  lista.
