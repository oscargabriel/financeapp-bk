# Tasks

## 1. Bruno primero, fallando

Skills: `bruno-cli`.

- [x] 1.1 `bruno/transactions/lote-con-elementos-invalidos.yml`: el elemento `[3]` pasa de `USD` a
  `"currencyCode": "DOLAR"` y sigue esperando `[3].currencyCode`.
- [x] 1.2 `bruno/transactions/`, al final de la carpeta (seq 39 en adelante), con el usuario de la
  carpeta:
  - `cuenta-usd.yml`: una cuenta `SAVINGS` en USD con saldo inicial 1000, que guarda `idUsd`;
  - `multimoneda-gasto-en-usd.yml`: gasto de 25 en la cuenta USD, que queda `CONFIRMED` con los
    originales en null;
  - `multimoneda-gasto-convertido.yml`: gasto de 41000 COP en la cuenta USD; espera `amount` 10,
    `PENDING`, `originalAmount` 41000 y `originalCurrencyCode` COP;
  - `multimoneda-transferencia-con-destino.yml`: 410000 de Efectivo (COP) a la cuenta USD con
    `destinationAmount` 95, que queda `CONFIRMED`;
  - `multimoneda-transferencia-sin-destino.yml`: 10 de la cuenta USD a Efectivo; espera `PENDING` y
    `destinationAmount` 41000;
  - `multimoneda-destino-misma-moneda.yml`: 400 en `[0].destinationAmount`;
  - `multimoneda-moneda-fuera-del-catalogo.yml`: `XYZ`, 400 en `[0].currencyCode`;
  - `multimoneda-pendientes.yml`: `GET /transactions/pending` con los dos pendientes y sus campos de
    moneda;
  - `multimoneda-saldos.yml`: la cuenta USD en 1070 (1000 − 25 + 95) y Efectivo bajando 410000;
  - `multimoneda-ajustar-destino.yml`: PATCH con `destinationAmount` 40000 sobre la transferencia
    pendiente;
  - `multimoneda-cuenta-de-otra-moneda.yml`: PATCH de un gasto en COP hacia la cuenta USD, 400 en
    `accountId`;
  - `multimoneda-destino-en-gasto.yml`: PATCH con `destinationAmount` sobre un gasto, 400 en
    `destinationAmount`.
- [x] 1.3 Correr la carpeta contra la app actual y ver que los requests nuevos fallan por la regla de
  COP.

## 2. Base de datos

- [x] 2.1 `schema.sql`:
  - `original_amount NUMERIC(18,4)` y `original_currency_code CHAR(3)`, con FK a `currencies`;
  - `ck_transactions_original`: las dos o ninguna, y el monto mayor que cero;
  - los comentarios de las dos columnas, y el de `amount`, que dice «en la moneda de la cuenta
    origen».
- [x] 2.2 `update/20261010_02_monto_original.sql`: aditivo, antes del despliegue, con la reversión
  comentada al final.
- [x] 2.3 Comparación de esquemas de `modelo-datos.md`: `sin diferencias`.
- [x] 2.4 Base local reconstruida (`design.md`, decisión 11):
  ```powershell
  psql -c "DROP SCHEMA finance CASCADE"
  psql -f schema.sql
  psql -f seed.sql
  psql -f cargar-datos-local.sql
  ```
- [x] 2.5 `modelo-datos.md`: la sección Multi-moneda describe el monto original y la conversión.

## 3. Dominio

Skills: `java-architect`.

- [x] 3.1 `ExchangeRate.convertir(monto, decimales)`: multiplica, redondea con HALF_EVEN y aplica el
  mínimo de una unidad. Se prueba en `ExchangeRateTest`.
- [x] 3.2 Record `OriginalAmount(amount, currencyCode)`. `Transaction` gana `destinationAmount` y
  `original` al final, y todas las construcciones se actualizan, `TransactionMother` incluido.
- [x] 3.3 `Currency` gana `decimalPlaces`, que `CurrencyR2dbcAdapter` lee. Se verifica con
  `CatalogControllerTest`, cuya respuesta no cambia, y con Bruno.

## 4. Alta en lote

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `CreateTransactionRequest`:
  - `currencyCode` con el formato de 3 letras;
  - `destinationAmount` con `@Positive` y `@MontoNumeric`;
  - `@ReglasDeTransferencia` rechaza `destinationAmount` fuera de una transferencia.

  Se prueba en `CreateTransactionRequestTest` y `TransactionControllerTest`.
- [x] 4.2 `ReferenciasDelUsuario`:
  - `cuentaPropia` deja de exigir COP;
  - un método nuevo exige COP con el mensaje «por ahora …», y lo usan
    `CreateRecurrenceUseCase`, `UpdateRecurrenceUseCase` y `tarjetaParaCuotas`.

  Se prueba en los tests de esos casos de uso.
- [x] 4.3 `TransactionBatchValidator`:
  - devuelve borradores con la moneda recibida y la del destino por convertir;
  - valida `currencyCode` contra las monedas activas;
  - valida `destinationAmount` entre cuentas de la misma moneda.

  Se prueba en `CreateTransactionsUseCaseTest`.
- [x] 4.4 `CreateTransactionsUseCase`:
  - lee las monedas activas;
  - convierte con `ResolveExchangeRatePort.resolve`, memorizado por par y fecha;
  - deja `PENDING` lo convertido;
  - traduce el 404 de `resolve` a 502.

  Se prueba en `CreateTransactionsUseCaseTest` con estos casos:
  - un gasto convertido;
  - una transferencia con y sin destino;
  - el redondeo de COP;
  - la memorización;
  - el 502;
  - el `currencyCode` inactivo.

## 5. Modificación y respuesta

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `UpdateTransactionRequest` y `UpdateTransactionCommand` ganan `destinationAmount`, que entra
  en `sinCambios`. Se prueba en `UpdateTransactionRequestTest` y `TransactionControllerTest`.
- [x] 5.2 `UpdateTransactionUseCase` aplica las reglas de moneda (`design.md`, decisión 7). Se prueba
  en `UpdateTransactionUseCaseTest`.
- [x] 5.3 `TransactionR2dbcAdapter`:
  - el INSERT escribe `destination_amount`, `original_amount` y `original_currency_code`;
  - el UPDATE escribe `destination_amount`;
  - las lecturas las devuelven.

  Lo verifica Bruno.
- [x] 5.4 `TransactionResponse` gana `destinationAmount`, `originalAmount` y `originalCurrencyCode`.
  Se prueba en `TransactionControllerTest`.

## 6. Documentación y Bruno personal

- [x] 6.1 `docs/contrato-api.md`: alta, PATCH y respuesta con los campos nuevos, las reglas de moneda
  y el 502.
- [x] 6.2 `docs/despliegue.md`: el update va antes del despliegue.
- [x] 6.3 `bruno-personal/`: el request de alta y el de PATCH de movimientos documentan `currencyCode`
  y `destinationAmount`, sin ejecutarse.

## 7. Verificación

- [x] 7.1 `gradlew build` en verde, con el conteo de tests y la cobertura.
- [x] 7.2 `verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos.
