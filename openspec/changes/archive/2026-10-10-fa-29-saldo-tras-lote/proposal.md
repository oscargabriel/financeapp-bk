## Why

FA-29. Al sacar Testcontainers de la suite, el trigger `trg_transactions_sync_balance` se quedó sin
cobertura en Gradle. Ese trigger mantiene `accounts.current_balance`, y un alta en lote lo dispara N
veces dentro de una misma transacción.

`docs/database/test-checks.sql` trae una consulta que compara el saldo del trigger contra el que se
deriva de los movimientos. La tarea la convierte en un paso reproducible del cierre de etapa. Hoy
tiene tres problemas:

- No está versionado, aunque `modelo-datos.md` y `test-data.sql` lo citan.
- Su comprobación 2 suma todos los movimientos, pero el trigger solo aplica los `CONFIRMED`. Con
  pendientes da un falso «no coincide», y el escenario los trae: `pendientes@` y los
  `multimoneda-*` de FA-51.
- Solo mira al usuario `prueba@`. El lote de `bruno/transactions/` usa un usuario propio por corrida.

Lo que el usuario decidió el 10-10-2026, escrito en la tarea:

- `test-checks.sql` se versiona en el PR de esta tarea.
- Se corrige la comprobación 2.
- El criterio 3 esperaba a FA-51, que ya está en Por revisar.

## What Changes

- **`docs/database/check-saldos.sql`, nuevo.** Revisa todas las cuentas de todos los usuarios:
  - el saldo esperado es el inicial más el efecto de los movimientos `CONFIRMED`, programados
    incluidos, con `destination_amount` en las transferencias entre monedas;
  - lista solo las cuentas descuadradas;
  - si hay alguna, termina con error (código distinto de 0) por medio de un `RAISE EXCEPTION`.
  - Solo lee.
- **`docs/database/test-checks.sql`** se versiona:
  - su comprobación 2 usa la misma regla, filtrada al usuario del escenario;
  - la comprobación 1 sigue igual.
- **`verificar-bruno.ps1`** corre `check-saldos.sql` contra la base local cuando termina `bru run`:
  - con una cuenta descuadrada, el script sale con código distinto de 0 aunque Bruno haya pasado;
  - así la comprobación corre en cada cierre, sin que haya que acordarse.
- **Documentación**: cómo correr la comprobación a mano y que el script la corre solo.
  - `AGENTS.md`, sección *Comandos*;
  - `modelo-datos.md`, sección *Probar en local*.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es herramienta de verificación, no comportamiento del API: el change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio | Estado |
|---|---|
| 1. Un request de Bruno da de alta un lote y otro verifica el saldo | YA EXISTÍA: `bruno/transactions/lote-de-varios.yml` y `saldos-tras-el-alta.yml` (FA-27) |
| 2. Documentado cómo correr la comprobación de `test-checks.sql` tras el lote | Se implementa: el script nuevo, `test-checks.sql` corregido, el paso en `verificar-bruno.ps1` y la documentación |
| 3. La transferencia entre monedas distintas queda cubierta | Por el API, YA EXISTÍA: `multimoneda-transferencia-con-destino`, `multimoneda-saldos` y `multimoneda-saldos-final` (FA-51). Por la base, se implementa: `check-saldos.sql` revisa `destination_amount` en todas las cuentas tras la corrida |

## Impact

- Archivos nuevos o que se versionan: `docs/database/check-saldos.sql` y `docs/database/test-checks.sql`.
- Se modifican:
  - `.claude/scripts/verificar-bruno.ps1`;
  - `AGENTS.md`;
  - `docs/database/modelo-datos.md`.
- Sin cambios en el código de la app, en el esquema ni en Bruno.

## Fuera de alcance

- Una prueba automatizada del trigger en Gradle: no hay base en la suite, por la decisión del
  14-09-2026.
- Correr la comprobación contra Neon. El script sirve igual, pero ahí no hay corrida de Bruno.
