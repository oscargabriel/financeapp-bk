# Proposal

Origen: FIN-104, del epic FIN-7 (Multimoneda y tasas de cambio). Bloquea a FIN-106, que lleva el
total al Inicio del front, y a FIN-107.

## Why

`GET /api/reports/balance` lista cada cuenta con su saldo en su moneda y no suma nada. El front arma
el rótulo «Saldo actual (solo débito)» sumando las cuentas `DEBIT` que están en la moneda base, así
que al cambiar la moneda base (FIN-29) el saldo de Inicio queda en 0: ninguna cuenta está en la
moneda nueva. El API tiene que entregar ese total ya convertido.

Decisión del epic que aplica (16-10-2026): el saldo débito suma todas las cuentas débito en la
moneda base y lo calcula una vista de la base, como la gráfica de gasto mensual. Cada movimiento va
con la tasa de su fecha (regla de `finance.in_currency`) y el saldo inicial con la tasa del día en
que se creó la cuenta. No se usa la tasa de hoy.

## What Changes

- La respuesta de `GET /api/reports/balance` gana un campo `debitBalance`, en `currencyCode`: el
  saldo vigente de las cuentas `DEBIT` activas y no borradas del usuario, de cualquier moneda,
  convertido a su moneda base.
  - El saldo inicial de cada cuenta se convierte con la tasa de la fecha local en que se creó la
    cuenta.
  - Cada movimiento confirmado y ya ocurrido se convierte con la tasa de su fecha local, con la
    regla de `finance.in_currency`. En una transferencia que entra en otra moneda cuenta
    `destinationAmount`, convertido con la tasa de su fecha.
  - Lo que ya está en la moneda base suma su monto tal cual, sin pasar por el dólar.
  - Pendientes y programados no cuentan, igual que en `currentBalance`.
  - Cambiar la moneda base cambia el total sin reescribir nada guardado.
- Una vista nueva, `finance.v_account_balance_in_base`, con el saldo vigente de cada cuenta no
  borrada en la moneda base de su dueño. La consulta de saldo suma ahí las débito activas. Su DDL va
  en `schema.sql`, `drop.sql`, un update nuevo y `modelo-datos.md`.
- `accounts` sigue igual: cada cuenta con su saldo en su moneda. Lo único que se suma es
  `debitBalance`.
- `docs/api/reportes.md` deja de decir «Las cuentas no se suman» y documenta el campo; se actualiza
  su fila en `contrato-api.md`.
- Escenario nuevo en `test-data.sql`, un usuario con cuentas débito en COP y en USD, una de ellas
  creada hace 50 días, para que `bruno/` compruebe la conversión contra la base real.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: la consulta de saldo trae `debitBalance`, el total de las cuentas débito en la moneda
  base. Cambian los requisitos *Cuentas en la consulta de saldo* y *Montos en la moneda de la
  persona*. Se agrega uno para el total débito.

## Impact

- Código: `Balance` (dominio), `BalanceR2dbcAdapter`, `BalanceResponse`, y sus tests. Sin puertos
  nuevos: el total sale en la misma consulta que las sumas.
- Base: vista nueva. Un update que solo crea una vista no modifica tablas, así que no reinicia los
  datos de la base local ni los del front.
- API: campo nuevo en una respuesta existente. No rompe a quien la consuma.
- El asistente (`ia`) no lee esta consulta: no cambia.

## Fuera de alcance

- Usar `debitBalance` en Inicio y el símbolo de la moneda en la gráfica: FIN-106, del front.
- Sumar otros tipos de cuenta (efectivo, ahorros, tarjetas) o dar un patrimonio total.
- Lo que pide FIN-107 (serie mensual completa, variación, últimos y más costosos, cupo usado).
- Convertir el `currentBalance` de cada cuenta a la moneda base.
