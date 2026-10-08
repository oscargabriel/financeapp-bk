# Design

## Context

`demo-data.sql` (FA-93) tiene el usuario, los ids de cuentas y categorías propias y el correo fijos
en variables `\set` al principio, y el resto del script los usa con `:'uid'`, `:'efectivo'`, etc.
`test-data.sql` es independiente y lo recarga `verificar-bruno.ps1`.

## Goals / Non-Goals

**Goals:**
- Cargar el mismo escenario en dos usuarios sin duplicar el script.
- Un comando que deje toda la base local lista después de un cambio de esquema.

**Non-Goals:**
- Escenarios distintos por usuario: `dev@` tiene los mismos datos que la demo, por decisión del
  usuario.

## Decisions

### 1. Parametrizar `demo-data.sql` con variables de psql

El script lee dos variables, `email` y `u` (un dígito). Si no las recibe, toma
`demo@financeapp.local` y `0`, con lo que corrido solo hace exactamente lo de hoy. Los ids se arman
con `u` en el primer bloque: `4000000<u>-…` para el usuario, `5000000<u>-…` para las cuentas y
`6000000<u>-…` para las categorías propias. La demo actual usa `40000000-…`, que es `u = 0`, así que
sus ids no cambian. `dev@` usa `u = 1`. La limpieza de `@front.local` solo corre cuando el correo es
el de la demo.

Descartado:
- **Copiar el script para `dev@`**: dos copias del mismo escenario se desalinean con el primer
  ajuste que pida el front.
- **Una función temporal (`pg_temp`) con el escenario dentro**: carga los dos usuarios en una sola
  llamada, pero pasa todo el script a PL/pgSQL, que es más difícil de leer y de ajustar que el SQL
  plano de hoy.

### 2. Script de entrada con `\ir`

`cargar-datos-local.sql` incluye `test-data.sql` y dos veces `demo-data.sql` con `\ir`, que resuelve
la ruta relativa al propio archivo, así que funciona desde cualquier carpeta. Cada script abre y
cierra su propia transacción: si uno falla, `ON_ERROR_STOP` detiene la carga y lo anterior queda
aplicado. Eso basta en local, porque volver a correr el comando lo deja todo bien.

### 3. El aviso de reinicio va en el update y en el PR

Por decisión del usuario, no se anuncia en el tablero. La regla queda en `AGENTS.md`: un `update/`
que modifique tablas dice en su encabezado que después de aplicarlo en local se corre
`cargar-datos-local.sql`, y el PR lo destaca. En Neon no aplica, porque ahí no se cargan datos de
prueba.

## Risks / Trade-offs

- [El front empieza a escribir datos y una recarga se los borra] → `AGENTS.md` dice que «recargar
  está permitido» vale solo mientras el front solo lea. La memoria de Claude lo recuerda para volver
  a tratar la demo como persistente cuando eso cambie.
- [Las variables `email` y `u` quedan definidas en la sesión de psql] → El script de entrada las
  fija antes de cada inclusión. Quien corra `demo-data.sql` a mano en una sesión que ya las tenga
  cargaría ese usuario; está documentado en el encabezado.
