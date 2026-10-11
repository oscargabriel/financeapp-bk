# Tasks

## 1. Efecto de un movimiento y código de error

Skills: `java-architect`, `java-exceptions`.

- [x] 1.1 `INSUFFICIENT_FUNDS` en `ErrorCodes`. `WebExceptionHandler` ya traduce cualquier `BadRequestException` con su status y sus errores, así que no cambia. Lo verifica `SaldoNoNegativoTest`, que comprueba el 409 con ese código y su `field`.
- [x] 1.2 `SaldoNoNegativo` en `application/usecase`, con `SaldoNoNegativoTest`:
  - efecto de gasto, ingreso, transferencia con y sin `destinationAmount`, pendiente y programado;
  - rechazo solo si el delta < 0 y el resultado < 0;
  - tarjeta `CREDIT` sin tope;
  - cuenta ya negativa que recibe dinero;
  - texto de la `description`;
  - ubicación del error del lote en el primer elemento que cruza el cero, uno por cuenta.

## 2. Alta en lote

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 2.1 Requests de Bruno en `bruno/transactions/`, escritos antes y fallando. Cada uno crea su propia cuenta `DEBIT` con saldo inicial conocido:
  - gasto mayor que el saldo → 409 en `[0].amount`, y el saldo no cambia;
  - lote compensado con un ingreso → 201;
  - error en el elemento que cruza el cero → `[1].amount`;
  - gasto programado mayor que el saldo → 201.
- [x] 2.2 `CreateTransactionsUseCase` valida el saldo después de convertir y antes de `saveAll`. Lo verifica `CreateTransactionsUseCaseTest`: 409 sin llamar a `saveAll`, lote compensado, pendientes y programados sin chequeo.

## 3. PATCH, DELETE y aprobación de movimientos

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 3.1 Requests de Bruno, escritos antes y fallando:
  - `PATCH` que sube un gasto → 409 en `amount`;
  - `PATCH` que reduce un ingreso ya gastado → 409 en `amount`;
  - `DELETE` de un ingreso ya gastado → 409 en `id`, y el ingreso sigue;
  - en `bruno/pending/`, aprobar un pendiente sin saldo → 409 en `amount`, y sigue pendiente.
- [x] 3.2 `UpdateTransactionUseCase` compara el efecto del resultante con el del guardado, después de los 400 existentes. Lo verifica `UpdateTransactionUseCaseTest`: subir monto, cambiar de cuenta, reducir ingreso, pasar ingreso a futura, bajar un gasto en cuenta negativa → 200.
- [x] 3.3 `DeleteTransactionUseCase` lee el movimiento, valida y borra. Lo verifica `DeleteTransactionUseCaseTest`: 404 igual que antes, borrar un gasto siempre se puede, ingreso ya gastado → 409 sin borrar, ingreso programado → 204.
- [x] 3.4 `ApprovePendingTransactionUseCase` valida después de las cuentas desactivadas. Lo verifica `ApprovePendingTransactionUseCaseTest`: 409 sin `confirm`, desactivada → solo `INVALID_STATE`, pendiente programado → 200.

## 4. Cuentas

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 4.1 Requests de Bruno en `bruno/accounts/`, escritos antes y fallando:
  - alta `SAVINGS` con `initialBalance` negativo → 400 en `initialBalance`;
  - parche con `initialBalance` negativo sobre una no crédito → 400;
  - parche que baja el inicial por debajo de lo gastado → 409 en `initialBalance`, y el nombre no cambia.
- [x] 4.2 Constraint `@SaldoInicialSegunElTipo` sobre `CreateAccountRequest`. La verifican `CreateAccountRequestTest` y `AccountControllerTest` (el slice web del alta).
- [x] 4.3 `UpdateAccountUseCase`: 400 por inicial negativo según el tipo guardado y 409 por inicial que deja el vigente < 0, sin llamar a `update`. Lo verifica `UpdateAccountUseCaseTest`.

## 5. Escenario, contrato y colección personal

Skills: `bruno-cli`.

- [x] 5.1 Correr la colección y ajustar los requests o el escenario de `test-data.sql` que ahora dan 409 porque dejaban cuentas no crédito en negativo, sin cambiar lo que cada uno verifica. Se anotan en las notas de la tarea.
- [x] 5.2 `docs/api/contrato-api.md`:
  - fila `INSUFFICIENT_FUNDS` en la tabla de errores;
  - el 409 y el 400 en `POST` y `PATCH /api/accounts`, `POST`, `PATCH` y `DELETE /api/transactions` y en Movimientos pendientes;
  - en Movimientos programados, que no se validan;
  - en Series recurrentes, la advertencia de FA-143.
- [x] 5.3 `bruno-personal/`: ninguna ruta, cuerpo ni autenticación cambia, y los requests nuevos son casos de error del escenario de pruebas. Se comprueba que no haya nada que replicar y se dice en el PR.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura ≥ 85 %. Resultado: 1154 tests, 0 fallos, cobertura de línea 98,5 % (2220 de 2253).
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo real de requests y asserts, y `check-saldos.sql` sin cuentas descuadradas. Resultado: 555 requests, 442/442 tests, 1090/1090 assertions; las 51 cuentas cuadran.
