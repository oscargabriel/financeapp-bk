# Tasks

## 1. Bruno del resumen de Inicio (RED)

Skills: `bruno-cli`.

- [x] 1.1 Carpeta `bruno/home/` (seq 9, justo después de `reports/`: las carpetas siguientes escriben movimientos del usuario del escenario, así que corren un lugar) con `folder.yml` y `resumen-escenario.yml` (`GET {{baseUrl}}/reports/home`, usuario del escenario): 6 meses del más antiguo al más reciente con los tres primeros en 0 y los tres últimos 1445000/1820000/1905500 de gasto y 4500000/4500000/5300000 de ingreso, `month` igual al mes en curso, `spent` 1905500, `spentChangePercent` 4.70, `latestExpenses` de 5 por `occurredAt` descendente y ninguno futuro ni transferencia, `topExpenses` de 5 del mes por `amountBase` descendente con el primero en `shareOfTop` 100, y cada tarjeta con `creditUsedPercent` igual a `−currentBalance/creditLimit×100` acotado y las demás cuentas en `null`. Verifica: `bru run home -r --env local` falla con 404
- [x] 1.2 `resumen-saldo.yml` con `saldo@financeapp.local` (login reutilizado de `bruno/reports/saldo-debito-login.yml` o propio): `latestExpenses` trae solo los dos confirmados, el de 10 USD con `amountBase` 39000; `debitBalance` 1551000. Verifica: falla con 404
- [x] 1.3 `resumen-sin-credenciales.yml` y `resumen-con-basic.yml`: 401 `UNAUTHENTICATED` en `authorization` con `WWW-Authenticate: Bearer`. Verifica: los dos pasan ya (la cadena JWT atiende la ruta) y siguen pasando al final
- [x] 1.4 `resumen-usuario-sin-datos.yml` con el usuario recién registrado de `bruno/monthly-spending/usuario-sin-datos-*`, o uno propio `@bruno.local`: 6 meses en cero, `spentChangePercent` `null`, listas vacías, `debitBalance` 0 y `accounts` vacía. Verifica: falla con 404

## 2. Dominio: el cálculo del resumen

Skills: `java-architect`.

- [x] 2.1 Record `HomeSummary` en `core/domain/model` con su fábrica a partir del mes, la moneda, los meses de `MonthlySpending`, los gastos, `debitBalance` y las cuentas: rellena la serie de 6 meses en orden ascendente, calcula `spent` y `spentChangePercent` (escala 2, `HALF_UP`, `null` con mes anterior en 0). Verifica: `HomeSummaryTest` (serie con huecos, sin meses, variación positiva, negativa y con anterior en 0)
- [x] 2.2 `shareOfTop` de cada gasto más costoso (escala 2, `HALF_UP`, el primero 100, lista vacía) y `creditUsedPercent` por cuenta (tarjeta con deuda 13.16, saldo a favor 0, pasada del cupo 100, sin cupo o cupo 0 `null`, otro tipo `null`). Verifica: `HomeSummaryTest`

## 3. Persistencia: últimos y más costosos

Skills: `java-architect`.

- [x] 3.1 `findLatestExpenses(userId, hasta, limite)` y `findTopExpenses(userId, desde, hastaDia, hasta, limite)` en `TransactionReportQueryPort` y en `TransactionReportR2dbcAdapter`, componiendo el `SELECT` existente, con `type = 'EXPENSE'`, `occurred_at <= :hasta` y su `ORDER BY ... LIMIT` (design.md). Verifica: lo prueba `bruno/home/` (los adapters R2DBC no tienen test en la suite) y `.\gradlew.bat compileJava`

## 4. Caso de uso, puerto de entrada y endpoint

Skills: `java-architect`.

- [x] 4.1 `GetHomeSummaryPort` y `GetHomeSummaryUseCase`: `ponerAlDia` una vez, zona del usuario para el mes en curso con el `Clock`, y en paralelo `MonthlySpendingQueryPort` (6 meses), `BalanceQueryPort.findSums` (días del mes), `AccountQueryPort` (activas), y las dos consultas nuevas con `hasta = clock.instant()` y límite 5. Verifica: `GetHomeSummaryUseCaseTest` (orden `ponerAlDia` antes de consultar, mes según la zona del usuario en el borde de fin de mes, argumentos pasados a cada puerto)
- [x] 4.2 `HomeSummaryController` (`GET /reports/home`) y `HomeSummaryResponse` en `dto/`, con los nombres de campo del contrato. Verifica: `HomeSummaryControllerTest` (200 con el cuerpo mapeado, parámetro desconocido ignorado) y `HomeSummaryIT` (401 sin credencial y con el Basic compartido)
- [x] 4.3 `bru run home -r --env local` con la app de la rama en verde: los requests de 1.1, 1.2 y 1.4 pasan de 404 a verde

## 5. Contrato y colección personal

- [x] 5.1 Sección `GET /api/reports/home` en `docs/api/reportes.md` (cuerpo de ejemplo, tabla de campos, «A tener en cuenta» con el mes en la zona del usuario y las reglas de pendientes y programados) y su fila en `docs/api/contrato-api.md`. Verifica: `grep -n "reports/home" docs/api/contrato-api.md docs/api/reportes.md`
- [x] 5.2 Replicar en `bruno-personal/` el request `GET /reports/home` (ruta y autenticación Bearer de esa colección), sin ejecutarlo. Verifica: el archivo existe y su ruta coincide con la de `bruno/home/resumen-escenario.yml`

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde con la cobertura sobre el umbral; anotar el conteo real de tests — 1734 tests, 0 fallos
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye `bru run . -r --env local` y `check-saldos.sql`); anotar los conteos reales de requests y tests — 1043 requests, 695 tests y 2078 assertions en verde; las 83 cuentas cuadran

## Workflow follow-up

- Archivar el change en la misma rama, PR a `dev` con merge inmediato, comentarios de cierre en FIN-107 y avisar en FIN-108 que el contrato está en `docs/api/reportes.md`.
