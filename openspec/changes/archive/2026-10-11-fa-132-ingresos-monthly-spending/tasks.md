## 1. Requests de Bruno en rojo

Skills: `bruno-cli`.

- [x] 1.1 `bruno/monthly-spending/gasto-mensual.yml`: un test con `totalIncome` de cada mes del
  escenario (5300000 en el mes en curso, 4500000 en los dos anteriores), uno que diga que las
  transferencias del mes en curso no suman a los ingresos, y `totalIncome` en el contrato completo.
  `gasto-mensual-mes-con-meta-sin-movimientos.yml`: `totalIncome` 0. Actualizar los `docs` de los dos.
  Verificación: la colección completa (`verificar-bruno.ps1 -RecargarDatos`) contra el código de `dev`
  muestra los nuevos en rojo; una carpeta suelta da 401 porque el token sale de `auth/login`.
- [x] 1.2 En `bruno/reports/`, después de `usd-gasto-mensual.yml`: `usd-ingresos.yml` (alta en lote en
  `idCuentaUsd`: un `INCOME` de 82000 hoy, uno de 39000 hace 100 días y uno de 50000 programado el día
  15 del mes siguiente, en una categoría de ingreso de la persona); `usd-ingresos-mensual.yml`
  (`monthly-spending` del mes de hace 100 días al mes en curso: el mes en curso trae `totalSpent` 10 y
  `totalIncome` 20; el de hace 100 días, `totalSpent` 0, `transactionCount` 0, `totalIncome` 10 y la
  meta en `null`); `usd-ingreso-programado.yml` (`monthly-spending` del mes siguiente: arreglo vacío).
  Si hace falta el id de la categoría de ingreso, sacarlo en `usd-categorias.yml`. Verificación:
  la misma corrida completa: `usd-ingresos-mensual` en rojo (`usd-ingreso-programado` ya pasa hoy,
  porque la vista ignora los ingresos; queda como guarda).

## 2. La vista y el campo nuevo

Skills: `java-architect`.

- [x] 2.1 `docs/database/update/20261011_02_ingresos_gasto_mensual.sql`: `CREATE OR REPLACE VIEW`
  de `finance.v_monthly_spending` con el CTE de la decisión 2 y `total_income` como última columna,
  y el `COMMENT ON VIEW` al día. Encabezado: va antes del despliegue de la app en Neon y no reinicia
  los datos locales. Mismo cambio en `docs/database/schema.sql`. Aplicarlo en la base local y correr
  la comparación de esquemas de `docs/database/modelo-datos.md`. Verificación:
  `psql -U postgres -d financeapp -c "SELECT total_income FROM finance.v_monthly_spending LIMIT 1"`
  y la comparación sin diferencias.
- [x] 2.2 `MonthlySpending` y `MonthlySpendingResponse` agregan `totalIncome` (al final, después de
  `transactionCount`); `MonthlySpendingR2dbcAdapter` lo lee de `total_income`; `MonthlySpendingMother`
  lo llena. Verificación: `MonthlySpendingControllerTest.devuelveLosMesesConElFormatoDelContrato`
  pide `$[0].totalIncome`, y `mantieneEnNullLosCamposDeMetaCuandoElMesNoTieneMeta` el de un mes sin
  meta, primero en rojo.
- [x] 2.3 Replicar en `bruno-personal/reportes/gasto-mensual.yml` lo que cambie del request (la
  ruta, el cuerpo y la autenticación no cambian; actualizar su `docs` con `totalIncome`), sin
  ejecutarlo.

## 3. Documentación

- [x] 3.1 `docs/api/contrato-api.md`, sección `GET /api/monthly-spending`: la descripción, el
  ejemplo con `totalIncome`, la fila de la tabla de campos, `transactionCount` como conteo de gastos
  y «Los meses vacíos no vienen» con el mes que solo tiene ingresos. Verificación: lectura de la
  sección.
- [x] 3.2 `docs/database/modelo-datos.md`, vista `v_monthly_spending`: la columna `total_income` y
  qué meses trae la vista. Verificación: lectura del párrafo.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests. Resultado: BUILD SUCCESSFUL,
  1180 tests, 0 fallos, cobertura sobre el umbral.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests y asserts, y `check-saldos.sql` sin cuentas descuadradas.
  Resultado (con `-Puerto 8081`, porque el 8080 lo ocupaba otra app): 591/591 requests, 469/469
  tests, 1141/1141 assertions; las 58 cuentas cuadran.
