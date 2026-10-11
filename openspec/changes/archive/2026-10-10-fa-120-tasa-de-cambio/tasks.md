# Tasks

## 1. Escenario y Bruno primero, fallando

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/test-data.sql`: la sección de tasas borra toda `finance.exchange_rates`,
  siembra `USD→COP` de hoy en 4100 y la de `CURRENT_DATE - 30` en 3900, las dos `MANUAL`, y dice
  por qué borra (`design.md`, decisión 7). Se verifica con la recarga de `verificar-bruno.ps1`.
- [x] 1.2 Carpeta `bruno/exchange-rates/`. Cada request calcula sus fechas relativas en un script
  previo. Requests:
  - USD→COP hoy: 4100, `rateDate` de hoy;
  - EUR→COP hoy: 5125, por el stub;
  - COP→COP: 1;
  - USD→COP hace 10 días: 3900, `rateDate` de hace 30 días;
  - USD→COP en 2000-01-01: 404 `NOT_FOUND` en `date`;
  - falta `from`: 400 en `from`;
  - `XTS` inactiva: 400 en `to`;
  - fecha mal formada: 400 en `date`;
  - sin credenciales: 401;
  - con Basic: 401.

  Verificar que la carpeta falla contra la app actual con 404 en la ruta.

## 2. Dominio y caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 Records `UsdRate` (moneda, tasa, `rateDate`) y `ExchangeRate` (`from`, `to`, `date`,
  `rate`, `rateDate`), con la fábrica que cruza dos patas a escala 10 `HALF_EVEN` y la del mismo
  par. Prueba: `ExchangeRateTest` (cruzada, inversa, pata USD, mismo par, `rateDate` la más vieja).
- [x] 2.2 Puertos de salida:
  - `ExchangeRateRepositoryPort`: `findLatestFromUsd(moneda, fecha)`, vacío si no hay fila ≤ fecha,
    y `saveFromUsd(fecha, tasas)`;
  - `ExchangeRateProviderPort`: `latestFromUsd()` → `Mono<Map<String, BigDecimal>>`.

  Puerto de entrada `ResolveExchangeRatePort.resolve(from, to, date)`.
- [x] 2.3 `ResolveExchangeRateUseCase` con `Clock`. Prueba: `ResolveExchangeRateUseCaseTest`, con
  estos casos:
  - mismo par sin tocar puertos;
  - cruce con las patas guardadas;
  - fecha pasada sin proveedor;
  - hoy sin la pata: proveedor una vez, guarda solo las activas sin USD, resuelve;
  - hoy con las patas: sin proveedor;
  - fecha futura;
  - proveedor con error y tasa anterior: la usa;
  - proveedor con error sin tasa: 404 `NOT_FOUND` en `date`;
  - fecha antigua: 404;
  - moneda inexistente: 400 en `from` o `to`.

## 3. Adapters de salida

Skills: `java-architect`, `java-logging`.

- [x] 3.1 `ExchangeRateR2dbcAdapter`:
  - la consulta de la fila más reciente ≤ fecha por `ix_exchange_rates_lookup`;
  - el `INSERT ... ON CONFLICT DO NOTHING` con `source = 'API'`.

  Lo verifica `bruno/exchange-rates/`; el adapter no tiene test en la suite.
- [x] 3.2 `ExchangeRateApiAdapter`, con `@ConditionalOnProperty(tasas.proveedor=exchangerate-api)`:
  - `WebClient` sobre `tasas.exchangerate-api.url`, con su timeout;
  - valida `result`, `base_code` y el rango de cada tasa;
  - en el log, status o tipo de error, nunca el cuerpo ni la URL.

  Prueba: `ExchangeRateApiAdapterTest`, con un servidor falso como `GeminiAssistantAdapterTest`:
  - respuesta válida;
  - `result` de error;
  - `base_code` distinto;
  - tasas fuera de rango descartadas;
  - 500 y timeout como error, con su log.
- [x] 3.3 `StubExchangeRateAdapter` con `@Profile("!prod")` y `tasas.proveedor=stub`, con el mapa de
  `design.md`. Prueba: `StubExchangeRateAdapterTest`.
- [x] 3.4 Configuración:
  - `tasas.*` con default en `src/main/resources/application.yaml`;
  - los mismos valores en `src/test/resources/application.yaml`, con la URL a `localhost:65535`.

  `CloudRunConfigTest` sigue en verde.

## 4. Endpoint

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `ExchangeRateController` en `GET /exchange-rates`:
  - parsea a mano `from`, `to` y `date`;
  - los obligatorios y el formato van dentro de `Mono.defer`, con un 400 `VALIDATION_ERROR` en su
    campo;
  - DTO `ExchangeRateResponse`.

  Prueba: `ExchangeRateControllerTest`.
- [x] 4.2 `ExchangeRatesIT`, con servidor real:
  - 401 `Bearer` sin credenciales;
  - 401 con Basic;
  - 404 fuera del base-path.

## 5. Script, documentación y colección personal

Skills: `bruno-cli`.

- [x] 5.1 `verificar-bruno.ps1` fija `TASAS_PROVEEDOR=stub` junto a `ASISTENTE_PROVEEDOR`. En
  `AGENTS.md`, el párrafo del stub nombra los dos.
- [x] 5.2 `docs/api/contrato-api.md`: el endpoint nuevo, con parámetros, respuesta y errores, y la
  nota de que el front debe mostrar «Rates By Exchange Rate API».
- [x] 5.3 Replicar en `bruno-personal/` un request de `GET /exchange-rates`, con la misma ruta y
  autenticación, sin ejecutarlo.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo real de requests y aserciones.
