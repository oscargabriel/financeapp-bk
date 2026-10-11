# Proposal

Tarea **FA-75** del tablero. Salió el 06-10-2026 al dividir la etapa de Telegram: «consultar
saldo» es una de las tres funciones que va a exponer el asistente de IA (FA-77), y hoy no existe.
El usuario la pidió con prioridad alta, antes de todo lo de la IA.

## Why

El API dice cuánto se gastó (`/monthly-spending`) y lista los movimientos con sus totales por tipo
(`/reports/transactions`), pero no responde la pregunta más directa: cuánto entró menos cuánto
salió, en un periodo y desde siempre. Tampoco hay un lugar que junte ese neto con lo que queda en
cada cuenta y con el cupo disponible de las tarjetas, que hoy solo se ve listando cuentas. El
asistente de FA-77 necesita esa respuesta en una sola consulta.

## What Changes

- Endpoint nuevo `GET /api/reports/balance`, con `from`/`to` opcionales (sin ellos, el mes en
  curso). Devuelve en una respuesta:
  - el neto del rango: ingresos, egresos y su resta;
  - el neto histórico, con todos los movimientos del usuario;
  - las cuentas activas con su saldo vigente y, en las tarjetas de crédito, el cupo y el
    disponible.
- `GET /api/reports/transactions` agrega un campo `net` (ingresos − egresos de la lista que
  devuelve). Es un campo nuevo, no cambia los existentes.
- Neto = ingresos − egresos sobre `amountBase`, en la moneda base del usuario. Las transferencias
  no entran: mover plata entre cuentas propias, incluido pagar la tarjeta, no es ni ingreso ni
  egreso. El neto puede salir negativo y se devuelve tal cual.
- Comprobación de solo lectura de que cada tarjeta de crédito tiene `credit_limit`, para que el
  disponible no salga en `null`. Se hace contra la base local: el usuario aclaró que los datos de
  Neon son de sus pruebas.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: dos requisitos nuevos, el de la consulta de saldo y el del neto del reporte de
  movimientos. La consulta de saldo va en esta capacidad porque es otra lectura agregada de los
  movimientos del usuario, bajo el mismo `/reports`.

## Fuera de alcance

- **Hacer obligatorio el cupo en las tarjetas.** Hoy `creditLimit` es opcional en una `CREDIT`: el
  alta lo admite vacío y el `PATCH` lo puede borrar. Cambiarlo es un cambio de contrato de
  `cuentas` y no lo pide la tarea. Si la comprobación muestra tarjetas sin cupo, se reportan
  para completarlas con `PATCH /api/accounts/{id}`, que ya existe. Si se quiere que no puedan
  existir sin cupo, va como tarea nueva.
- **Tocar Neon.** La comprobación es un `SELECT` sobre la base local. Completar un cupo es dato del
  usuario y lo hace él, o se hace con su visto bueno explícito.
- **Movimientos pendientes de aprobación.** Ese estado nace en FA-76, que decide cómo los excluye
  de esta consulta.
- **Un saldo total sumando cuentas.** Las cuentas tienen monedas distintas y no hay conversión del
  saldo de una cuenta a la moneda base: se devuelve cada cuenta en su moneda, sin sumarlas.

## Impact

- Código nuevo: puerto de entrada y caso de uso de la consulta de saldo, un puerto de salida con su
  adapter R2DBC para el agregado de ingresos y egresos, el endpoint en `TransactionReportController`
  y el DTO de respuesta. El caso de uso reutiliza `AccountQueryPort` para las cuentas.
- Código modificado: `TransactionReport` (el neto) y `TransactionReportResponse` (el campo `net`).
- API: una ruta nueva y un campo nuevo, compatible hacia atrás. `docs/api/contrato-api.md` se
  actualiza con los dos.
- Bruno: carpeta `reports/` con los requests de la consulta de saldo y la aserción de `net` en el
  reporte del mes, replicados en `bruno-personal/`.
- Sin cambios de esquema.
