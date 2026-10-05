# Design

## Context

El único reporte, `GET /api/monthly-spending`, lee la vista `v_monthly_spending`. Esa vista agrupa por
`date_trunc('month', occurred_at AT TIME ZONE u.timezone)`: el mes se corta en la zona de cada
usuario, guardada en `users.timezone`. La propiedad `app.timezone` (bean `Clock`) solo se usa para
saber qué mes es "hoy" cuando faltan `from` o `to`.

`transactions` guarda `amount` en la moneda de la cuenta y `amount_base` en la moneda base del
usuario. Los reportes suman `amount_base`. El índice `ix_transactions_user_date (user_id,
occurred_at DESC)` ya existe.

## Goals / Non-Goals

**Goals:**
- Que un mismo movimiento caiga en el mismo día y el mismo mes en este reporte y en el gasto
  mensual.
- Que los totales cuadren siempre con la lista que se devuelve.

**Non-Goals:**
- Paginación, tope de rango, exportación.
- Reescribir el gasto mensual sobre la consulta nueva.

## Decisions

### El día se corta con `users.timezone`, no con `app.timezone`

El criterio de la tarea dice `app.timezone`. Se cambia por la zona del usuario porque es la que usa
`v_monthly_spending`: con zonas distintas, un gasto podría salir en octubre en el gasto mensual y en
noviembre en este reporte. Hoy las dos valen `America/Bogota`, así que en la práctica no cambia
nada; la diferencia aparece cuando haya un usuario en otra zona.

Descartado: `app.timezone` pasado como parámetro a la consulta. Un solo valor para todos los
usuarios contradice la columna que el modelo ya tiene para esto.

### Límites del rango calculados sobre el día, no sobre la columna

La condición compara `occurred_at` con dos instantes calculados en SQL a partir de la zona del
usuario: `occurred_at >= (:from::timestamp AT TIME ZONE u.timezone)` y `occurred_at < ((:to + 1)::timestamp
AT TIME ZONE u.timezone)`. Así la consulta usa `ix_transactions_user_date`.

Descartado: `(occurred_at AT TIME ZONE u.timezone)::date BETWEEN :from AND :to`. Es más legible,
pero aplicar una función a la columna impide usar el índice.

### Los totales se calculan en Java sobre la lista

El adapter devuelve los movimientos del rango, ya filtrados. Un objeto de dominio arma
`totalsByType` y `totalsByCategory` recorriendo esa lista.

- Los totales cuadran con la lista por construcción: salen de las mismas filas.
- La regla (qué tipos aparecen, el cero, el orden) se prueba con JUnit puro. La suite no tiene base,
  así que un `GROUP BY` en SQL solo lo verificaría Bruno.

Descartado: dos consultas de agregación más en SQL. Serían tres lecturas del mismo rango, que
podrían no coincidir si entre una y otra entra un movimiento, y dejarían la regla de los totales
fuera de la suite. Su ventaja —no traer las filas— no aplica: el reporte devuelve las filas igual.

### Filtros repetibles o separados por coma

`categoryId` y `type` se reciben como lista: `?type=EXPENSE&type=INCOME` y `?type=EXPENSE,INCOME`
valen lo mismo. Los valores en blanco se ignoran y los repetidos se colapsan. El controlador
convierte cada valor a mano (UUID y enum), como pide AGENTS.md para los parámetros, y el error lleva
el nombre del parámetro como campo.

En SQL los filtros se agregan como fragmentos fijos (`AND t.category_id = ANY(:categoryIds)`,
`AND t.type = ANY(:types)`) solo cuando vienen, con los valores siempre por bind. Nunca se concatena
un valor del cliente.

Descartado: dos parámetros con nombre en plural (`categoryIds`, `types`). La forma singular repetible
es la que genera un formulario HTML o `URLSearchParams` al agregar valores.

### La moneda base sale en la respuesta aunque no haya movimientos

`currencyCode` se lee de `users.base_currency_code` con una consulta aparte, para que un rango vacío
la traiga igual. Sin ella, el cliente no sabe en qué unidad están los totales.

### `from` posterior a `to` lo decide el caso de uso

El controlador valida formato y presencia; el orden del rango lo comprueba el caso de uso dentro de un
`Mono.defer`, como hace el gasto mensual con `MonthRange`. El error va sobre `from`.

## Risks / Trade-offs

- [Un rango de varios años devuelve miles de filas en una respuesta] → Aceptado: uso personal. Si
  aparece un cliente que lo necesite, la paginación es una tarea aparte.
- [Un usuario que cambie de zona horaria ve sus días históricos cortados con la zona nueva] → Igual
  que el gasto mensual hoy; no se guarda la zona por movimiento.
- [`categoryName` de una categoría borrada lógicamente] → Se devuelve igual: el movimiento sigue
  apuntando a ella y el nombre es el que tenía.
