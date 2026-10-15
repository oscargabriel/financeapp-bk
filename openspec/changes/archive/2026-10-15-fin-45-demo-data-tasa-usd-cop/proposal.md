# Proposal

FIN-45, epic FIN-74 (Calidad y mantenimiento). Apareció en FIN-43 el 12-10-2026, al ensayar la
carga de la demo en una base vacía.

## Why

`demo-data.sql` aborta con `FX001` (`No hay tasa de cambio USD→COP para el movimiento`) en una base
que solo tiene `schema.sql` y `seed.sql`: el trigger `set_usd_equivalent` necesita alguna tasa
`USD→COP` y la única fuente local de tasas es `test-data.sql`. Su encabezado dice que «corrido solo
carga la demo del front», y en una base nueva no es cierto. En Neon hubo que insertar a mano una
tasa antes de cargarla.

## What Changes

- `demo-data.sql` inserta una tasa `USD→COP` fija de respaldo **solo si no existe ninguna** tasa de
  ese par, antes de los movimientos. Si ya hay alguna —las de `test-data.sql` en local, las reales
  en Neon— no toca la tabla.
- La tasa de respaldo va fechada antes del movimiento más antiguo de la demo, nunca hoy, para no
  ocupar la fila del día que el proveedor guardaría (ver `design.md`).
- El encabezado de `demo-data.sql` dice qué tasa trae, cuándo la inserta y que correrlo contra Neon
  es seguro.
- `docs/despliegue.md` (*La demo en Neon*) y la línea de `AGENTS.md` que exige la tasa antes de la
  demo se ajustan: la tasa real previa pasa de obligatoria a recomendada.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: cambia un script de datos, no el comportamiento del sistema (`skip_specs: true`).

## Fuera de alcance

- Cambiar las tasas de `test-data.sql` o los totales que verifica `bruno/`.
- Que `demo-data.sql` consulte al proveedor real de tasas: psql no hace HTTP, y el procedimiento de
  Neon ya trae la tasa real antes.
- Movimientos en USD en la demo: la cuenta en dólares sigue sin movimientos.

## Impact

- `docs/database/demo-data.sql`
- `docs/despliegue.md`
- `AGENTS.md` (una línea de *Base de datos*)
- Sin cambios en `src/`, `bruno/` ni el contrato del API.
