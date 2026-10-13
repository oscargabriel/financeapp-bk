# Proposal

Origen: FIN-41, migrada de Notion (FA-117). Surgió en FA-108 (compras en cuotas), y FA-115 y FA-116
la dejaron fuera de alcance.

## Why

Una compra en cuotas crea sus cuotas como gastos programados de la tarjeta. El issue pregunta qué
pasa con esas cuotas cuando la tarjeta se desactiva o se borra, y pide resolverlo con la misma regla
que las series (FA-116).

- **Borrar ya está resuelto.** FA-115 rechaza con 409 el borrado de una cuenta con movimientos
  programados, y las cuotas por venir lo son: el escenario «Tarjeta con cuotas por venir» de
  `cuentas` lo cubre. A diferencia de una serie sin fin, una compra no vuelve a crear cuotas, así que
  no queda el hueco que FA-116 cerró para las series.
- **Desactivar no tiene una regla escrita.** Hoy la tarjeta se desactiva, la compra sigue activa y
  sus cuotas siguen venciendo, pero ninguna spec lo dice. El issue no aclaraba si eso era un error.

## What Changes

- Decisión tomada con el usuario el 13-10-2026, entre tres opciones:
  - dejar las compras como están (la elegida);
  - rechazar la desactivación con 409, como las series;
  - cancelar las compras al desactivar.

  El porqué está en `design.md`.
- **Desactivar una tarjeta con compras activas responde 200.** Las compras y sus cuotas no cambian.
  Las cuotas siguen venciendo en su fecha y mueven el saldo de la tarjeta, que es deuda ya contraída.
- **La compra sigue manejándose igual con la tarjeta desactivada.** Aparece en
  `GET /api/installment-purchases`, se edita con `PATCH` y se cancela con `DELETE`.
- **Borrar la tarjeta sigue respondiendo 409** mientras tenga cuotas por venir. La regla de FA-115 no
  cambia: se agrega a la spec de `cuotas` un requisito que remite a ella y la cubre también con la
  tarjeta desactivada.
- `contrato-api.md`, la colección `bruno/` y los `docs` de `bruno-personal/` al día.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuotas`: requisito nuevo «Compras sobre una tarjeta desactivada o borrada».
- `cuentas`:
  - escenario nuevo en «Desactivar y reactivar una cuenta»: una tarjeta con compras activas se
    desactiva;
  - escenario nuevo en «Solo se borra una cuenta sin movimientos programados»: una tarjeta
    desactivada con cuotas por venir tampoco se borra.

## Impact

- Código Java: sin cambios. El comportamiento elegido es el que el código ya tiene: desactivar no
  consulta las compras, y ni el listado, ni la edición, ni la cancelación miran el estado de la
  tarjeta. El saldo vigente descuenta lo programado por fecha (`finance.scheduled_balance_delta`) sin
  mirar `is_active`.
- API: no cambian rutas, cuerpos, respuestas ni códigos. Queda escrito un comportamiento que no tenía
  regla.
- Base de datos: sin cambios.
- Bruno: requests nuevos al final de `bruno/installments/`, sobre `Visa USD` y su compra, que la
  carpeta ya crea.

## Fuera de alcance

- Borrar una categoría que usan las cuotas por venir de una compra. FA-116 bloqueó el borrado de una
  categoría con series activas, pero no con compras. Es otro recurso, y el issue solo habla de la
  tarjeta. Si hace falta, va en un issue aparte.
- Mostrar en `GET /api/accounts` que una tarjeta desactivada todavía tiene cuotas por venir. Hoy solo
  sale con `includeInactive=true`. Eso es del front o de un issue aparte.
- Una restricción en la base que respalde las reglas.
