# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Asistente

- [x] 1.1 Fila de `POST /api/assistant/messages` en el índice y sección propia: Bearer, cuerpo
  `{"data"}` con sus reglas, los cinco `intent` con el objeto que trae cada uno, y los errores 400,
  401 y 502. Verificar: contrastado con `AssistantController`, `AssistantMessageRequest`,
  `AssistantMessageResponse`, `AssistantReply` y `AssistantUseCase`, y con la spec `asistente`.
- [x] 1.2 Fila 502 `EXTERNAL_SERVICE_ERROR` en la tabla de errores. Verificar: `ErrorCodes` y
  `GeminiAssistantAdapter` (status 502, `field` `server`).
- [x] 1.3 *Movimientos pendientes*: los crea el asistente. Sale de «todavía no tiene» el ítem de crear
  pendientes. Verificar: `TransactionOrigin.estadoInicial` y `AssistantUseCase.crear`.

## 2. Cuentas

- [x] 2.1 `isActive` en el parche de `PATCH /api/accounts/{id}`, con lo que implica desactivar, y
  fuera de las filas que lo rechazan. Fila `isActive` en la tabla de campos de una cuenta. Sale de
  «todavía no tiene». Verificar: `UpdateAccountRequest`, `UpdateAccountUseCase` y la spec `cuentas`
  (*Desactivar y reactivar una cuenta*).
- [x] 2.2 `availableCredit` de `reports/balance` igual al de `GET /accounts`. Verificar:
  `BalanceResponse.AccountItem` usa `Account.availableCredit()`.

## 3. Movimientos y formato

- [x] 3.1 `origin` en el ejemplo de respuesta del alta y en su texto, con `WEB`, `TELEGRAM` e `IMPORT`.
  Verificar: `TransactionResponse` y los usos de `TransactionOrigin`.
- [x] 3.2 *Formato*: la excepción de `installments`. Verificar: `@JsonInclude(NON_NULL)` en
  `InstallmentPurchaseResponse`.

## 4. Verificación

- [x] 4.1 `gradlew build` en verde con el conteo real (no cambia código; confirma que nada se rompió).
- [x] 4.2 `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales.
