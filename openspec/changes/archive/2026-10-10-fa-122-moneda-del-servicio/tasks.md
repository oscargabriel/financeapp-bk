# Tasks

## 1. Escenario y Bruno primero, fallando

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/test-data.sql`: las tasas `USD→COP` pasan a hoy 4100, hace 35 días 4100 y
  hace 45 días 3900 (`design.md`, decisión 8).
- [x] 1.2 `bruno/exchange-rates/`:
  - `tasa-anterior.yml` pide hace 40 días y espera 3900 con `rateDate` de hace 45;
  - `sin-tasa.yml` pasa a «Fecha anterior a todas las tasas»: 200, 3900, `rateDate` de hace 45.
- [x] 1.3 `bruno/reports/`, al final de la carpeta: una persona `usd-<timestamp>@bruno.local` con
  moneda USD.
  - Pasos: registro, login, cuenta COP y dos gastos, de 41000 hoy y de 39000 hace 50 días.
  - `reports/transactions` de hace 50 días a hoy: `currencyCode` USD, `amountBase` 10 y 10, total
    de gastos 20.
  - `reports/balance` del mismo rango: `expense` 20.
  - `monthly-spending` del mes: `currencyCode` USD, y `totalSpent` 10, o 20 si hace 50 días cae en
    el mismo mes, cosa que no pasa.
  - Verificar que la carpeta falla contra la app actual: los montos salen en COP.

## 2. Base de datos

- [x] 2.1 `schema.sql`:
  - `finance.usd_rate(currency, on_date)` y `finance.in_currency(target, currency, amount,
    amount_base, on_date)` (`design.md`, decisiones 3 y 4);
  - el trigger `trg_transactions_usd_equivalent` con `finance.set_usd_equivalent()` (decisiones 1, 2
    y 6);
  - las dos vistas de gasto mensual suman `in_currency`;
  - comentarios de `amount_base`, `exchange_rate`, `users.base_currency_code` y `budgets.amount`.
- [x] 2.2 `update/20261010_01_moneda_del_servicio.sql`, con la reversión comentada al final:
  - las funciones;
  - el aborto si hay movimientos de una moneda sin tasas;
  - el recálculo con los triggers de saldo y fecha deshabilitados;
  - el trigger;
  - las vistas y los comentarios.

  Aplicarlo en local y correr `cargar-datos-local.sql`.
- [x] 2.3 Comparación de esquemas de `modelo-datos.md`: `sin diferencias`.
- [x] 2.4 Verificación manual con psql en la base local, con el resultado anotado en la evidencia:
  - un gasto de 41000 COP con tasa 4100 da `amount_base` 10;
  - cambiar solo la descripción no mueve `exchange_rate`;
  - cambiar el monto sí lo recalcula;
  - una moneda sin tasas da `FX001`.

## 3. Lectura de tasas y reportes

Skills: `java-architect`.

- [x] 3.1 `ExchangeRateR2dbcAdapter.findLatestFromUsd` lee `finance.usd_rate`. Javadoc del puerto:
  la más antigua cuando no hay anterior. Lo verifica `bruno/exchange-rates/`.
- [x] 3.2 `ResolveExchangeRateUseCase`: el 404 solo sin ninguna fila. Prueba:
  `ResolveExchangeRateUseCaseTest`, renombrando los casos de 404 a «sin ninguna tasa».
- [x] 3.3 `TransactionReportR2dbcAdapter` (`amount_base` por `in_currency`) y `BalanceR2dbcAdapter`
  (sumas por `in_currency`), con Javadoc de `TransactionReportQueryPort` y `TransactionReport`. Lo
  verifica `bruno/reports/`.

## 4. Escrituras

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `ResolveExchangeRatePort.refreshToday(currencies)`, implementado con el `alDia` existente;
  un fallo del proveedor se absorbe. Prueba: `ResolveExchangeRateUseCaseTest` (falta hoy: una
  llamada; hay hoy: ninguna; proveedor caído: completa).
- [x] 4.2 `CreateTransactionsUseCase` llama `refreshToday` con las monedas de los elementos fechados
  hoy o después antes de guardar, y no lo llama si todos son pasados. Prueba:
  `CreateTransactionsUseCaseTest`.
- [x] 4.3 `FX001` a 502 `EXTERNAL_SERVICE_ERROR` en `server`:
  - en `TransactionR2dbcAdapter` (`saveAll`, `update`), `RecurrenceR2dbcAdapter` e
    `InstallmentPurchaseR2dbcAdapter`, con un traductor común en `persistence`;
  - prueba: un test unitario del traductor.
- [x] 4.4 Comentarios que decían «solo COP, `amount_base = amount`»: `TransactionR2dbcAdapter`,
  `RecurrenceR2dbcAdapter`, `ReferenciasDelUsuario` y `CreateTransactionRequest`.

## 5. Documentación y colección personal

- [x] 5.1 `modelo-datos.md`: multi-moneda con los tres niveles, el trigger y las vistas.
- [x] 5.2 `contrato-api.md`:
  - `amountBase` y los totales en la moneda de la persona, con la regla;
  - la tasa más antigua en `GET /api/exchange-rates`;
  - el 502 sin tasa en el alta.
- [x] 5.3 `despliegue.md`: el orden. Primero la app, luego `GET /api/exchange-rates` contra el
  servicio, luego el update.
- [x] 5.4 `bruno-personal/`: sin cambios de ruta, cuerpo ni autenticación. Revisar que nada asuma
  montos en COP fijos, sin ejecutarlo.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo real. Los totales en COP del resto de la colección quedan iguales.
