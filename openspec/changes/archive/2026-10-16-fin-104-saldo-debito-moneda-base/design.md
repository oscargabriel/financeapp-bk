# Design

## Context

`BalanceR2dbcAdapter` calcula `period` y `allTime` en una sola consulta, con
`MontoDeLaPersona.DE_T` (`finance.in_currency`). Las cuentas salen aparte, de `AccountQueryPort`, con
el `currentBalance` en su moneda: `accounts.current_balance` lo mantienen triggers en la moneda de
la cuenta, y el saldo vigente le resta `finance.scheduled_balance_delta`. Ninguna columna guarda el
saldo convertido, y no puede guardarlo: depende de la moneda base, que cambia sin reescribir nada.

El epic fija el cálculo (proposal.md, *Why*): una vista, tasa de la fecha de cada aporte.

## Goals / Non-Goals

**Goals:**
- Que el total lo dé la base, en la misma consulta que las sumas, sin otro viaje.
- Que las cifras de una cuenta en la moneda base cuadren exactamente con su `currentBalance`.

**Non-Goals:**
- Reemplazar `current_balance` ni sus triggers.
- Que el asistente (`ia`) lea esta vista.

## Decisions

### Una vista por cuenta, no por usuario

`finance.v_account_balance_in_base`: una fila por cuenta no borrada, con `account_id`, `user_id`,
`account_type`, `is_active`, `currency_code` (la base del dueño) y `balance`. La consulta de saldo
suma ahí con `account_type = 'DEBIT' AND is_active`, como subconsulta escalar dentro de la consulta
que ya existe, así un usuario sin cuentas débito sale con 0 por el `COALESCE`.

- Descartado: una vista `v_debit_balance` por usuario con el filtro dentro. Esconde en el DDL cuáles
  cuentas cuentan, que es justo lo que el front y la spec nombran. La vista por cuenta deja el
  filtro en la consulta, a la vista de quien la lee, y sirve tal cual si otro reporte necesita el
  saldo convertido de otro tipo de cuenta.
- Descartado: calcularlo en Java a partir de `accounts`. Necesitaría las tasas de cada fecha de
  cada movimiento, que es reimplementar `finance.in_currency`.

### Rehacer el saldo desde los movimientos, no convertir `current_balance`

El saldo inicial va con `finance.amount_in_currency(base, moneda_cuenta, initial_balance,
fecha_local(created_at))`. Cada movimiento con `finance.balance_delta` aplicado a
`finance.in_currency(base, t.currency_code, t.amount, t.amount_base, fecha_local(occurred_at))`,
con el mismo filtro que el saldo vigente: `CONFIRMED` y `occurred_at <= now()`. Lo que entra por una
transferencia: si no trae `destination_amount`, está en la moneda del movimiento y va por
`in_currency`; si lo trae, está en la moneda de la cuenta destino y va por `amount_in_currency` con
la fecha del movimiento.

- Descartado: `current_balance` vigente por la tasa de hoy. Lo descarta el epic (16-10-2026).
- Descartado: `current_balance` por la tasa de la fecha de creación. No refleja que cada
  movimiento valía lo de su fecha.

Las dos funciones devuelven el monto tal cual cuando ya está en la moneda base, y el signo lo da
`balance_delta`, como en el trigger: para una cuenta en la moneda base, `balance` es exactamente su
`currentBalance`. Esa igualdad es la que comprueba el escenario del usuario de `test-data.sql`.

`fecha_local` es `(x AT TIME ZONE u.timezone)::date`, el mismo corte que ya usan las vistas de gasto
mensual y el trigger que fija `amount_base`.

### El total en `Balance.Sums` y en `Balance`

`findSums` ya devuelve lo que sale de la base para la consulta de saldo. Se le agrega
`debitBalance`, que pasa a `Balance` y a `BalanceResponse` como campo de primer nivel. No hay
puerto nuevo ni cambio en el caso de uso más allá de copiar el valor.

- Descartado: un objeto `debit: { balance }` o meterlo en `allTime`. `allTime` es ingresos menos
  gastos, que no es un saldo (no lleva saldos iniciales ni transferencias). Un campo suelto junto a
  `currencyCode` deja claro en qué moneda va.

### Escenario propio en `test-data.sql`

El usuario `saldo@financeapp.local` (moneda COP, `America/Bogota`). La API no deja fijar
`created_at` de una cuenta, y sin una cuenta creada antes de la tasa de hoy no hay cómo distinguir
la tasa de creación de la de hoy. Agregar las cuentas al usuario `prueba@` cambiaría la lista de
cuentas que comprueban varias carpetas de `bruno/`.

| Cuenta | Tipo | Moneda | Inicial | Creada |
|---|---|---|---|---|
| Débito pesos | DEBIT | COP | 1.000.000 | ahora |
| Débito dólares | DEBIT | USD | 100 | hace 50 días |
| Débito apagada | DEBIT, inactiva | COP | 80.000 | ahora |
| Efectivo saldo | CASH | COP | 300.000 | ahora |

Movimientos, todos de la cuenta en pesos salvo el gasto en USD: ingreso de 500.000 y gasto de
200.000 de hoy; gasto de 10 USD hace 40 días en «Débito dólares»; transferencia de 410.000 COP a
«Débito dólares» con `destination_amount` 100, de hoy; transferencia de 100.000 a «Efectivo saldo»,
de hoy; un gasto pendiente de 50.000 y uno programado de 70.000 en el mes siguiente. «De hoy» es una
hora antes de `now()`: las tasas de hoy y de hace 35 días valen las dos 4100, así que una corrida
justo después de medianoche da lo mismo.

Las cifras de la spec salen de ahí. En USD, tras el `PATCH`: la cuenta en pesos aporta
243,9024 − 48,7805 + 121,9512 − 100 − 24,3902 = 192,6829 (cada aporte redondeado a 4 decimales por
su función), y la de dólares 100 − 10 + 100 = 190.

Bruno vuelve a poner COP al final, para que la carpeta pase dos veces sin recargar.

## Risks / Trade-offs

- [La vista recorre los movimientos de cada cuenta en cada consulta] → Va filtrada por `user_id`
  desde la consulta de saldo, y la cantidad de movimientos de un usuario es pequeña. Lo mismo hacen
  ya las vistas de gasto mensual.
- [Si la moneda base no tiene ninguna tasa, las funciones devuelven `NULL`] → El mismo
  comportamiento que la gráfica y los totales de hoy; la moneda base solo se puede elegir entre las
  que tienen tasa (FIN-29).
- [Dos copias de la regla del saldo vigente, el trigger y la vista] → El escenario de `prueba@`
  compara `debitBalance` con `currentBalance` de Bancolombia en cada `bru run`: si se separan,
  falla.

## Migration Plan

Update `docs/database/update/20261016_01_saldo_en_moneda_base.sql`, que solo crea la vista: no
modifica tablas ni reinicia datos, y va **antes** del despliegue de la app, porque la consulta nueva
la lee. Rollback: `DROP VIEW finance.v_account_balance_in_base` después de revertir la app.
