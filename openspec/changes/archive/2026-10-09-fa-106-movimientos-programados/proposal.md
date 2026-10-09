# Proposal

Origen: FA-106. Salió de un pedido hecho desde la raíz de coordinación el 09-10-2026, junto con la tasa
de las tarjetas (FA-105), los gastos recurrentes (FA-107) y las compras en cuotas (FA-108). El usuario
confirmó el mismo día que la regla vale para todo movimiento con fecha posterior al momento actual,
también los que se cargan a mano. El primer criterio de la tarea pide decidir el contrato; lo decidido
está en `design.md`.

## Why

Las cuotas de una compra y las ocurrencias de un gasto recurrente se van a crear antes de su fecha.
Hoy un movimiento confirmado mueve el saldo en cuanto se inserta, y los reportes cuentan todo lo
confirmado sin mirar la fecha, así que crear las cuotas por adelantado restaría del saldo plata que
todavía no se ha gastado. FA-107 y FA-108 esperan a esta tarea.

## What Changes

- Un movimiento confirmado con fecha posterior al momento actual está **programado**: no cuenta en el
  `currentBalance` de sus cuentas ni en los totales de `reports/transactions`, `reports/balance` y
  `monthly-spending`. Cuando llega su fecha empieza a contar solo, porque todo se calcula al leer: no
  hay ningún proceso que tenga que correr, y el servicio puede haber estado apagado.
- La respuesta de un movimiento gana `scheduled` (booleano) en `POST /api/transactions`,
  `PATCH /api/transactions/{id}`, la lista de pendientes y la aprobación.
- `GET /api/reports/transactions` **lista** los programados del rango, marcados con `scheduled: true`,
  para que el front los muestre distinto en Mis Gastos, pero **no los suma** en `totalsByType`,
  `totalsByCategory` ni `net`.
- Cambiar con el PATCH la fecha de un movimiento, de futura a pasada o al revés, deja el saldo como
  corresponde a la fecha nueva.
- Los movimientos con fecha pasada no cambian su efecto: el update no toca ninguna fila.
- `availableCredit` sigue siendo `creditLimit + currentBalance`, con el saldo vigente.

No es un cambio incompatible: `scheduled` es un campo nuevo, y hoy no hay movimientos con fecha futura
salvo los que el usuario haya cargado así a propósito.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: un requisito nuevo para el movimiento programado y su efecto en el saldo, otro para
  `scheduled` en la respuesta, y la modificación de fecha entre futura y pasada.
- `reportes`: los tres reportes dejan fuera de sus totales lo programado; el reporte de movimientos lo
  lista marcado; el escenario de frontera de mes pasa a un mes ya cumplido.
- `cuentas`: `currentBalance` es el saldo vigente, sin lo programado.

## Fuera de alcance

- Las cuotas (FA-108) y los gastos recurrentes (FA-107): esta tarea solo define cómo cuenta un
  movimiento con fecha futura.
- El cupo disponible de una tarjeta con compras en cuotas: FA-108 decide si baja por el total desde
  el día de compra. Aquí `availableCredit` usa el saldo vigente.
- Borrar una cuenta con movimientos programados: hoy el borrado mira el saldo vigente, y los
  programados se quedarían en la cuenta borrada y contarían en los reportes al llegar su fecha. Va
  como tarea nueva del tablero.
- Un endpoint o un filtro para listar solo los programados.
- `demo-data.sql`: sus movimientos del mes en curso con fecha posterior a hoy quedan programados, lo
  que le sirve al front para ver la marca. Sus montos no los fija ninguna spec.

## Impact

- Esquema: función `finance.scheduled_balance_delta(account_id)` con el efecto de los programados de
  una cuenta, y las vistas `v_monthly_spending` y `v_monthly_spending_by_category` filtran lo que aún
  no ha ocurrido. El trigger de saldos no cambia; cambia el comentario de `current_balance`. En
  `schema.sql` y como update en `docs/database/update/`, con su reversión.
- Datos: `test-data.sql` recorta a `now()` los movimientos del mes en curso, para que ninguno quede
  programado y los totales del escenario no dependan del día en que se corra. La cena de fin de mes
  que prueba la frontera del día pasa a un mes ya cumplido.
- Dominio: `Transaction` sabe si está programado a un instante dado, y `TransactionReport` arma sus
  totales sin los programados.
- Aplicación: `GetTransactionReportUseCase` recibe el `Clock`.
- Web: `TransactionResponse` y el ítem de `TransactionReportResponse` ganan `scheduled`;
  `TransactionController` recibe el `Clock`.
- Persistencia: `AccountR2dbcAdapter` devuelve el saldo vigente; `BalanceR2dbcAdapter` filtra lo
  programado.
- `bruno/`: una carpeta nueva para lo programado, las aserciones de frontera de mes reapuntadas, y el
  campo nuevo en las listas exactas de claves.
- `docs/api/contrato-api.md` y `docs/database/modelo-datos.md`.
