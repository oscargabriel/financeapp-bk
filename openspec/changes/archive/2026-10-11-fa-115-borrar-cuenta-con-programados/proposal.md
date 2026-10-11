# Proposal

Origen: FA-115. Surgió al proponer FA-106 (movimientos programados), el 09-10-2026, y FA-106 la dejó
fuera de alcance. Se relaciona con FA-116 (series sobre una cuenta borrada) y FA-117 (cuotas sobre
una tarjeta borrada).

## Why

`DELETE /api/accounts/{id}` solo exige saldo vigente en cero, y los movimientos programados (fecha
futura) no cuentan en ese saldo. Una cuenta en cero con un gasto programado se puede borrar, y ese
gasto se queda en la cuenta borrada. Al llegar su fecha mueve el saldo y cuenta en los reportes,
aunque la cuenta ya no aparezca en ningún listado.

Con las cuotas (FA-108) y las series (FA-107) esto pasa seguido:
- una compra con cuotas por venir siempre tiene programados;
- una serie sin fin activa siempre tiene una ocurrencia futura.

## What Changes

- Regla nueva: **no se borra una cuenta que tiene movimientos programados**, como origen o como
  destino. Programado es un movimiento `CONFIRMED` cuyo `occurredAt` todavía no llega. Los
  pendientes no cuentan: el borrado ya se los lleva (FA-97).
- El rechazo es 409 `RESOURCE_IN_USE` en el campo `id`. Su `description` dice cuántos programados
  tiene la cuenta y qué hacer: cancelar la serie o la compra en cuotas, o borrar los programados.
- Si la cuenta además tiene saldo, la respuesta trae los dos errores: el de `currentBalance` y el de
  `id`. Así el usuario ve todo lo que falta en una sola respuesta.
- Un borrado rechazado no cambia nada: la cuenta, sus pendientes y sus programados se quedan.
- Decisión tomada con el usuario el 11-10-2026, entre tres opciones:
  - rechazar el borrado (la elegida);
  - borrar los programados con la cuenta, como los pendientes;
  - dejarlos y solo documentarlo.

  El porqué está en `design.md`.
- `contrato-api.md` y la colección `bruno/` al día.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: requisito nuevo «Solo se borra una cuenta sin movimientos programados».

## Impact

- Código:
  - `DeleteAccountUseCase`;
  - un método nuevo en `AccountRepositoryPort`, que cuenta los programados de la cuenta;
  - su implementación en `AccountR2dbcAdapter`.
- API: un 409 nuevo en `DELETE /api/accounts/{id}`, sin códigos nuevos. No cambian rutas, cuerpos ni
  autenticación, así que `bruno-personal/` solo necesita la nota en su `docs`.
- Base de datos: sin cambios.
- FA-116 y FA-117: su parte de «borrar» queda resuelta por esta regla. Una cuenta con una serie
  activa o con cuotas por venir ya no se puede borrar. La parte de «desactivar» sigue en esas
  tareas.

## Fuera de alcance

- Qué pasa con las series y las cuotas al **desactivar** una cuenta → FA-116 y FA-117.
- Que una serie vuelva a crear su ocurrencia futura después de que el usuario la borre a mano y
  borre la cuenta → FA-116, que decide qué hace la puesta al día con una cuenta borrada.
- Una restricción en la base que respalde la regla. La comprobación vive en el caso de uso, como la
  del saldo (ver `design.md`).
