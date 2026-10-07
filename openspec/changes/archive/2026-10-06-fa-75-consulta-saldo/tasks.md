# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 En `bruno/reports/reporte-mes.yml`, agregar `net` a las claves del contrato y un test
  `net` = 3394500. En `reporte-filtro-tipo.yml` (`type=income`): `net` 5300000. En
  `reporte-rango-vacio.yml`: `net` 0. Nuevos `reporte-neto-gastos` (`type=EXPENSE` → `net`
  -1905500) y `reporte-neto-transferencias` (`type=TRANSFER` → `net` 0).
- [x] 1.2 Agregar a `bruno/reports/` los requests de la consulta de saldo, con `seq` a continuación,
  contra el usuario del escenario (`{{accessToken}}`). Las cifras salen de `test-data.sql`.
  - `saldo-mes`: `GET /reports/balance?from={{primerDia}}&to={{ultimoDia}}` → 200 con las claves
    `currencyCode`, `period`, `allTime` y `accounts`. `period`: 5300000 / 1905500 / 3394500.
    `allTime`: 14300000 / 5170500 / 9129500. `accounts` por nombre: Ahorros USD, Bancolombia,
    Efectivo y Visa, sin Nequi ni Davivienda. Visa con `currentBalance` -658000, `creditLimit`
    5000000 y `availableCredit` 4342000. Ahorros USD con `currencyCode` USD y cupo en `null`.
  - `saldo-sin-parametros`: sin query → el mismo `period` y `allTime` que `saldo-mes`, con
    `period.from` = `{{primerDia}}` y `period.to` = `{{ultimoDia}}`.
  - `saldo-mes-anterior`: primer y último día del mes anterior, calculados en el script del request
    → `period` 4500000 / 1820000 / 2680000, y el mismo `allTime`.
  - `saldo-negativo`: del día 2 al último del mes anterior → `period` 0 / 620000 / -620000.
  - `saldo-borde-ultimo-dia`: `from` = `to` = `{{ultimoDia}}` → `period.expense` 50000 (la cena de
    las 21:30, que en UTC ya es del mes siguiente).
  - `saldo-otro-usuario`: con el token del segundo usuario de la carpeta, que ya tiene un gasto en
    el mes → `period` y `allTime` con solo ese gasto, y `accounts` solo con su cuenta.
  - `saldo-solo-from` → 400 `VALIDATION_ERROR` en `to`. `saldo-solo-to` → 400 en `from`.
  - `saldo-fecha-mal-formada`: `from=2026/10/01` → 400 en `from`.
  - `saldo-rango-invertido`: `from=2026-10-31&to=2026-10-01` → 400 en `from`.
  - `saldo-sin-credenciales` → 401 `UNAUTHENTICATED` en `authorization` con `WWW-Authenticate: Bearer`.
    `saldo-con-basic` → 401.
- [x] 1.3 Actualizar el `docs` de `bruno/reports/folder.yml` con los netos del escenario por mes y el
  histórico.
- [x] 1.4 Verificar que los requests nuevos fallan hoy por la razón correcta. Desde `bruno/`, correr
  `bru run reports -r --env local` después de `auth/`. Hoy el reporte no trae `net`, y
  `/reports/balance` da 404 con Bearer (los dos 401 pasan desde ya, porque la cadena JWT cubre
  cualquier ruta).

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `TransactionReportTest` en RED: `net()` es INCOME − EXPENSE de `totalsByType`, negativo
  con solo gastos, cero con solo transferencias, y un tipo filtrado fuera cuenta como cero.
- [x] 2.2 Agregar `net()` a `TransactionReport`. Verificar con la clase en verde.
- [x] 2.3 `BalanceTest` en RED: el record de la consulta de saldo (`Balance`, con `currencyCode`, el
  periodo con `from`/`to`, los dos `Totals` de ingresos y egresos con su `net()`, y las `Account`)
  calcula cada neto como resta, incluido el negativo. Agregar `BalanceMother` en `support/`.
- [x] 2.4 Crear el modelo. Verificar con `BalanceTest` en verde.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `GetBalanceUseCaseTest` en RED, con `Clock` fijo:
  - sin `from` ni `to`, llega al puerto el primer y el último día del mes del `Clock`;
  - con los dos, llegan tal cual;
  - solo uno → `BadRequestException` `VALIDATION_ERROR` en el campo del que falta, sin llamar a
    ningún puerto;
  - `from` > `to` → 400 en `from`, sin llamar a ningún puerto;
  - el resultado junta la moneda base, los totales del puerto de salida y las cuentas activas de
    `AccountQueryPort.findByUser(userId, false)`.
- [x] 3.2 Crear `GetBalancePort` (entrada), `BalanceQueryPort` (salida: moneda base, y totales del
  rango y del histórico) y `GetBalanceUseCase`, con la validación dentro de `Mono.defer`. Verificar
  con la clase en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `TransactionReportControllerTest` en RED:
  - `GET /reports/balance` sin query llega al puerto con `from` y `to` en `null`;
  - `from=2026/10/01` y `from=2026-02-30` → 400 en `from` sin llamar al puerto;
  - la respuesta tiene la forma de la spec (`period`, `allTime`, `accounts` con `creditLimit` y
    `availableCredit` en `null` fuera de `CREDIT`);
  - el reporte de movimientos trae `net`.
- [x] 4.2 `TransactionReportIT` en RED: `/api/reports/balance` da 401 sin credencial y con el Basic
  compartido.
- [x] 4.3 Agregar el endpoint a `TransactionReportController`. Reutilizar el parseo de días, pero
  con fechas opcionales: el que falta llega como `null` y lo resuelve el caso de uso. Crear
  `BalanceResponse` y agregar `net` a `TransactionReportResponse`. Verificar con las dos clases en
  verde.

## 5. Persistencia

- [x] 5.1 `BalanceR2dbcAdapter`: una sola consulta con `SUM(amount_base) FILTER (WHERE ...)` para
  ingresos y egresos del rango y del histórico, con `COALESCE` a cero, y el corte del rango con
  `u.timezone`, como el reporte (design.md, decisión 2). Todos los valores por bind. Queda fuera de
  la cobertura por el patrón `*R2dbcAdapter`: lo verifican los requests del grupo 1.2 en verde.

## 6. Contrato, cupo de las tarjetas y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: la fila de `GET /api/reports/balance` en la tabla de rutas, su
  sección (parámetros, respuesta, errores, qué entra en el neto), y `net` en la de
  `GET /api/reports/transactions`. Verificar leyendo las dos secciones.
- [x] 6.2 `bruno-personal/reportes/saldo.yml`: el request de la consulta de saldo, con `from`/`to`
  deshabilitados por defecto, y `net` mencionado en el `docs` de `movimientos.yml`. Comparar ruta,
  parámetros y autenticación con `bruno/reports/`. No se ejecuta.
- [x] 6.3 Correr contra la base local el `SELECT` de solo lectura de la decisión 6 del design y
  reportar las tarjetas sin `credit_limit` (el usuario pidió validar en local: los datos de Neon son
  de sus pruebas). Verificar con la salida de psql copiada en las notas de la tarea.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los requests, tests y aserciones reales.
