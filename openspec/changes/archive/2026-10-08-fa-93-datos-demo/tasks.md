# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Script de demo

- [x] 1.1 `docs/database/demo-data.sql`: encabezado (qué es, contraseña, cómo correrlo), borrado de
  `demo@financeapp.local` y de los `%@front.local`, usuario con el hash de `test-data.sql`, copia de la semilla de categorías
  y dos categorías propias (una sin movimientos). Verificar: corre con `psql -v ON_ERROR_STOP=1`.
- [x] 1.2 Cuentas con id fijo: efectivo, débito, crédito con cupo, día de corte y día de pago,
  ahorros, inversión, una COP con saldo cero y una USD, todas sin desactivar ni borrar. Verificar:
  consulta de `finance.accounts` del usuario con tipo, moneda y saldo.
- [x] 1.3 Movimientos fijos cruzados con los meses 0, -1, -2, -4 y -5, variables por mes con
  decimales y montos grandes, transferencias, y el mes -3 con tres movimientos. Ninguno en la
  cuenta USD, en la de saldo cero ni con fecha posterior a `now()`. Verificar: conteo por mes en hora
  de Bogotá y que ninguna categoría no compatible con su tipo tenga movimientos.
- [x] 1.4 Metas globales y por categoría en algunos meses, una superada. Verificar: consulta de
  `finance.v_monthly_spending` del usuario.
- [x] 1.5 Re-ejecutable: correrlo dos veces seguidas y comparar los conteos y la suma de saldos de
  las dos corridas.
  Resultado (08-10-2026): las dos corridas dan 7 cuentas, 24 categorías, 106 movimientos, 11 metas,
  suma de saldos 29.814.272,33 y suma de montos 72.279.142,23. Por mes (gastos/ingresos/
  transferencias): 2026-05 14/4/3, 06 16/3/3, 07 2/1/0, 08 17/3/3, 09 18/3/3, 10 10/1/2. Cero
  movimientos con categoría incompatible, en USD o Nequi, o con fecha futura; Jardín sin movimientos.

## 2. Documentación

- [x] 2.1 `AGENTS.md`, sección *Base de datos*: qué es `demo-data.sql`, que no se carga en Neon,
  cómo cargarlo, con qué usuario y contraseña entrar, la tabla de usuarios de cada lado (back:
  `prueba@`, `inactivo@`, `@bruno.local`; front: `demo@`, `@front.local`), y que vaciar la base
  también lo borra.
- [x] 2.2 Tarea nueva en Notion (`Backlog`, `Área = Backend`): base local separada para el front y
  escenario de pruebas más grande, a diseñar en conjunto. Verificar: la fila existe con su
  `## Contexto`. Resultado: FA-94.

## 3. Verificación

- [x] 3.1 Con `demo-data.sql` cargado, `verificar-bruno.ps1 -RecargarDatos` en verde con los
  conteos reales (recarga `test-data.sql`, así que comprueba que los dos conviven).
  Resultado: 257/257 requests, 218/218 tests, 583/583 aserciones.
- [x] 3.2 Con la app levantada con el perfil `local`: `POST /api/auth/login` del usuario de demo →
  200, y con su token `GET /api/monthly-spending`, `GET /api/reports/balance` y
  `GET /api/reports/transactions` de los últimos seis meses devuelven datos. Registrar aquí los
  números.
  Resultado: login 200. `monthly-spending?from=2026-05&to=2026-10`: 200 con los seis meses (julio sin
  meta, junio al 204,64 %, octubre 2.351.900,90 de 4.500.000). `reports/balance` de 2026-05-01 a
  2026-10-31: 200, ingresos 35.370.957,03, gastos 25.408.185,20, neto 9.962.771,83, siete cuentas
  (Mastercard Oro -4.377.514,70 con cupo disponible 3.622.485,30). `reports/transactions` del mismo
  rango: 200, 106 movimientos.
- [x] 3.3 `gradlew build` en verde con los conteos reales, aunque el change no toque `src/`.
  Resultado: 637 tests, 0 fallos, 0 omitidos; cobertura de línea 98,24 % (1117/1137). Corrido en un
  worktree limpio de la rama: en el working tree del usuario falla `CloudRunConfigTest` por un
  cambio suyo sin commitear en `application.yaml` (`${SPRING_PROFILES_ACTIVE:local}`), ajeno a este
  change.
- [x] 3.4 `openspec validate fa-93-datos-demo --strict` en verde.
