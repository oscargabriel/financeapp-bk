# Design

## Context

`set_usd_equivalent` resuelve la tasa con `finance.usd_rate`: la `USD→X` más reciente con fecha
menor o igual a la del movimiento y, si no hay, la más antigua guardada. Con una sola fila del par,
cualquier fecha la encuentra. Falla con `FX001` solo cuando el par no tiene ninguna.

El proveedor (spec `tasas-de-cambio`, *Consulta al proveedor bajo demanda*) guarda la fila de hoy
con `ON CONFLICT DO NOTHING` y solo consulta si falta la de hoy: una fila `MANUAL` de hoy lo deja sin
consultar y nunca se sobrescribe.

`cargar-datos-local.sql` corre `test-data.sql` (que vacía `exchange_rates` y deja tres tasas) y
después dos veces `demo-data.sql`. `verificar-bruno.ps1 -RecargarDatos` solo recarga `test-data.sql`.

## Goals / Non-Goals

**Goals:** que `demo-data.sql` cargue sobre `schema.sql` + `seed.sql` sin pasos previos, sin alterar
las tasas cuando ya hay alguna.

**Non-Goals:** que la tasa de respaldo sea la real del mercado.

## Decisions

### Insertar la tasa solo si el par no tiene ninguna

`INSERT … SELECT … WHERE NOT EXISTS (SELECT 1 FROM finance.exchange_rates WHERE from = 'USD' AND to = 'COP')`,
después del borrado del usuario y antes de los movimientos.

- **Descartado: `ON CONFLICT DO NOTHING` sobre la fecha.** Agregaría una fila más en una base que ya
  tiene tasas, también en Neon y también después de `test-data.sql`, y cambiaría la tasa que
  `usd_rate` encuentra para las fechas cercanas a la de respaldo.
- **Descartado: abortar con un mensaje claro.** El criterio pide que cargue sin errores en una base
  nueva.
- **Descartado: que la demo incluya `test-data.sql`.** Recargaría el escenario de Bruno y en Neon
  metería sus usuarios.

### Fechada el día 1 de hace seis meses, en hora de Bogotá

El movimiento más antiguo de la demo es de hace cinco meses (`mes` 0 a 5). Una fecha anterior la
deja como la tasa «anterior o igual» de todos, sin pasar por la regla de la más antigua.

- **Descartado: hoy.** Una fila `MANUAL` de hoy impediría que el proveedor guarde la tasa real del
  día al primer pedido de la app, y en Neon quedaría como la tasa vigente.

### Valor 4000 COP por dólar, `source` `MANUAL`

Un número redondo del orden real, que no se confunde con una tasa del proveedor. La demo solo tiene
movimientos en COP, así que el valor solo se ve si un usuario de la demo pasa a ver dólares.

## Risks / Trade-offs

- [En una base nueva de Neon cargada sin el paso de la tasa real, queda una tasa ficticia de hace
  seis meses] → `docs/despliegue.md` mantiene la tasa real como primer paso; la ficticia solo pesa
  en fechas anteriores a la primera tasa real y queda marcada `MANUAL`.
