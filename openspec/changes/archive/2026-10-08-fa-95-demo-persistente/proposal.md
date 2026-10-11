# Proposal

Origen: FA-95. Llegó como nota del usuario el 08-10-2026, después de cerrar FA-93: «como nota el
front tiene solo acceso a la aplicacion de front y se esta conectando a la base de datos por eso
queria que tubiera datos persistentes». Le pregunté si agregaba la nota en FA-94 y en `AGENTS.md`, y
respondió «agregalo».

## Why

`AGENTS.md` describe `demo-data.sql` como un script que se puede recargar cuando haga falta. Desde
el lado del back es así, pero el front no corre psql ni puede recargar nada: lo que crea desde su
aplicación con `demo@financeapp.local` o con usuarios `@front.local` vive solo en la base local.
Quien recargue la demo o vacíe la base sin saberlo le borra al front su trabajo. Además, las fechas
de la demo se calculan al cargarla y no avanzan: al cambiar de mes, el mes en curso queda sin
movimientos de demo.

## What Changes

- `AGENTS.md`, sección *Base de datos*, un párrafo nuevo:
  - la demo es persistente para el front, porque el front no puede recargarla;
  - no se recarga `demo-data.sql` ni se vacía la base sin acordarlo antes;
  - `verificar-bruno.ps1 -RecargarDatos` y `test-data.sql` no la tocan;
  - sus fechas no avanzan con los meses, y cómo renovarlas sin borrar está pendiente en FA-94.
- FA-94 recoge la nota del usuario y un criterio nuevo: renovar los meses de la demo sin borrar lo
  que haya creado el front. Se hizo en Notion al tomar esta tarea.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es documentación.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- El script que renueva los meses sin borrar, y la base separada para el front: FA-94.

## Impact

- Solo `AGENTS.md`.
