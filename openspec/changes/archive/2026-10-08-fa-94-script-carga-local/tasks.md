# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Scripts

- [x] 1.1 `demo-data.sql` parametrizado con `email` y `u` (por defecto `demo@financeapp.local` y
  `0`), ids derivados de `u` y limpieza de `@front.local` solo para la demo. Verificar: corrido solo
  deja a `demo@` con los mismos ids y conteos de antes (7 cuentas, 24 categorías, 11 metas y los
  movimientos del día).
  Resultado: `demo@` con id `40000000-…-000000000001`, 7 cuentas, 24 categorías, 11 metas y 107
  movimientos (uno más que el 08-10 por la mañana: el de las 10:00 de ese día ya había ocurrido).
- [x] 1.2 `docs/database/cargar-datos-local.sql`: `test-data.sql`, la demo de `demo@` y la de `dev@`
  (`u = 1`). Verificar: correrlo dos veces y comparar por usuario los conteos y la suma de saldos;
  `demo@` y `dev@` dan los mismos números y no comparten ningún id.
  Resultado: las dos corridas dan `demo@` y `dev@` con 7 cuentas, 24 categorías, 107 movimientos,
  11 metas y saldos que suman 29.766.382,08; `prueba@` con 6 cuentas, 25 categorías, 25 movimientos y
  7 metas. `dev@` usa `40000001-…` y `50000001-…`, y ninguna cuenta es compartida.

## 2. Documentación

- [x] 2.1 `AGENTS.md`, sección *Base de datos*: el comando de carga, `dev@financeapp.local` en la
  tabla de usuarios, el front solo lee y recargar está permitido (reemplaza el párrafo de FA-95), y
  la regla de que un `update/` que modifique tablas reinicia los datos con el comando de carga y lo
  avisa en su encabezado y en el PR.
- [x] 2.2 `docs/database/modelo-datos.md`: los dos scripts nuevos en el listado de `docs/database/`, y
  `cargar-datos-local.sql` en lugar de `test-data.sql` en los dos niveles de *Reiniciar el estado*,
  que también se llevan los datos del front. Apareció al implementar: sin esto, ese documento
  seguiría mandando a recargar solo lo de bruno.

## 3. Verificación

- [x] 3.1 Con los datos cargados por el comando nuevo, `verificar-bruno.ps1 -RecargarDatos` en verde
  con los conteos reales.
  Resultado: 257/257 requests, 218/218 tests, 583/583 aserciones.
- [x] 3.2 Con la app con el perfil `local`: login de `demo@` y de `dev@` en 200, y
  `GET /api/reports/balance` de los seis meses con datos para los dos.
  Resultado: los dos con login 200 y balance 200 de 2026-05-01 a 2026-10-31, ingresos
  35.370.957,03, gastos 25.456.075,45, neto 9.914.881,58 y 7 cuentas.
- [x] 3.3 `gradlew build` en verde con los conteos reales.
  Resultado: 637 tests, 0 fallos, 0 omitidos; cobertura de línea 98,24 % (1117/1137). Las tareas
  salieron al día porque `src/` no cambió desde la última corrida.
- [x] 3.4 `openspec validate fa-94-script-carga-local --strict` en verde.
