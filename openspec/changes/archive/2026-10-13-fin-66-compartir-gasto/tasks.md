# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`. Carpeta nueva `bruno/shares/`. Registra en cada corrida a A, B y C con correo
`@bruno.local` (`test-data.sql` los borra al recargar), con una cuenta `DEBIT` en COP con saldo para
cada uno, una cuenta en USD para B y las categorías por defecto del registro; A envía solicitudes a B
y a C y las dos se aceptan. Cada request falla mientras el endpoint no lo cumpla.

- [x] 1.1 Preparación: `registro-*.yml`, `login-*.yml`, `cuentas-*.yml`, `categorias-*.yml`,
  `afiliar-*.yml` (A con B y A con C, enviar y aceptar; B con C, para la afiliación ajena), guardando
  tokens, ids de cuenta, de categoría y de afiliación.
- [x] 1.2 Alta: `compartir-gasto.yml` (90000 con 30000 a B y 30000 a C; 201, `shares` con dos
  `PENDING`, `sharedBy` y `sharedFrom` en `null`); `saldo-tras-compartir.yml` (A baja 90000);
  `pendiente-de-b.yml` (sin cuenta ni categoría, `sharedBy` A, `sharedFrom` con 90000 y la
  descripción, `groupId` no nulo); `pendientes-de-a.yml` (A no ve los de B ni de C).
- [x] 1.3 Errores del alta: `parte-en-cero.yml` (`[0].shares[1].amount`), `parte-sin-afiliacion.yml`,
  `persona-repetida.yml` y `partes-superan-total.yml` (`[0].shares`), `ingreso-con-partes.yml`,
  `afiliacion-inexistente.yml`, `afiliacion-sin-aceptar.yml` (A comparte con una solicitud suya
  `REQUESTED` a un correo sin cuenta), `afiliacion-ajena.yml` (A comparte con la afiliación aceptada
  entre B y C), los tres 400 en `[0].shares[0].affiliationId`, `lote-con-una-invalida.yml` (`[1].shares[0].amount` y nada guardado: los pendientes de B
  no cambian), `alta-con-partes-sin-credencial.yml` y `alta-con-partes-con-basic.yml` (401).
- [x] 1.4 Pagar: `aprobar-sin-cuerpo.yml` (400 en `accountId` y `categoryId`),
  `aprobar-cuenta-ajena.yml` (400 `accountId`), `aprobar-categoria-ingreso.yml` (400 `categoryId`),
  `aprobar-sin-saldo.yml` (409 `INSUFFICIENT_FUNDS` en `amount` con una cuenta de saldo menor),
  `aprobar-ajeno.yml` (A aprueba el de B: 404), `aprobar-con-cuerpo-sin-credencial.yml` (401),
  `pagar-parte-b.yml` (200, `CONFIRMED`, la cuenta y la categoría elegidas), `saldo-b-tras-pagar.yml`,
  `ingreso-de-a.yml` (pendiente de 30000 con `sharedBy` B), `aprobar-ingreso-a.yml` (200, el saldo de A
  sube 30000).
- [x] 1.5 Rechazar y lo inmutable: `patch-parte-c.yml` y `delete-parte-c.yml` (409 `INVALID_STATE` en
  `status`), `confirmar-grupo-parte-c.yml` (409 en `[0].accountId`), `patch-monto-con-pendientes.yml`
  (A: 409 en `amount`), `patch-tipo-con-pendientes.yml` (409 en `type`),
  `patch-descripcion-con-pendientes.yml` (200), `rechazar-parte-c.yml` (204),
  `reporte-de-a.yml` (el gasto con la parte de B `PAID` y la de C `REJECTED`; otro gasto sin compartir
  con los tres campos en `null`), `patch-monto-sin-pendientes.yml` (200).
- [x] 1.6 Borrar: un gasto nuevo compartido con B y C; B paga; `borrar-compartido.yml` (204);
  `pendientes-de-c-tras-borrar.yml` (ya no está); `reporte-de-b.yml` (su gasto confirmado sigue, con
  `sharedBy` A).
- [x] 1.7 Monedas: `compartir-en-usd.yml` (A, 90 USD con cuenta COP y 30 USD a B: el de A `PENDING`
  convertido, el de B en USD); `pagar-con-cuenta-cop.yml` (B paga con su cuenta en COP: `CONFIRMED`,
  `originalAmount` 30, `originalCurrencyCode` USD, `amount` según la tasa del stub);
  `rechazar-gasto-usd-compartido.yml`: un segundo gasto en USD compartido, A lo rechaza y el pendiente
  de B desaparece.
- [x] 1.8 Transferencia: `transferir-compartido.yml` (100000 a B 60000 y C 40000, con categoría de
  gasto: 201, `"type": "EXPENSE"`, `"shares": null`); `ingreso-transferido-b.yml` (pendiente
  `INCOME` con `sharedBy` A y `sharedFrom` `null`); `aprobar-ingreso-transferido.yml`;
  `transferir-no-suma.yml`, `transferir-con-destino.yml`, `transferir-sin-categoria.yml`,
  `transferir-con-monto-destino.yml` (400 en su campo).
- [x] 1.9 Desvincular: A comparte con C; `desvincular-c.yml`; `pagar-tras-desvincular.yml` (C paga:
  200, A recibe el ingreso); `compartir-tras-desvincular.yml` (400 en `[0].shares[0].affiliationId`).
- [x] 1.10 `bruno/recurrences/serie-con-partes.yml` y `bruno/installments/compra-con-partes.yml` y
  `simular-con-partes.yml` (400 `VALIDATION_ERROR` en `shares`).

## 2. Alta con partes

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 `ShareRequest` (`affiliationId` obligatorio; `amount` `@Positive` y `@MontoNumeric`) y
  `shares` en `CreateTransactionRequest` como `List<@Valid ShareRequest>`. Constraint de clase nueva
  en `dto/validation/` para lo que cruza campos: afiliación repetida, suma frente al total (`<=` en
  gasto, `=` en transferencia), partes en un `INCOME`, todo en `shares`. `@ReglasDeTransferencia`:
  con partes, sin destino ni monto de destino y con categoría. Verifica `CreateTransactionRequestTest`
  y que `TransactionsIT` saque los campos `[0].shares[1].amount` por el handler.
- [x] 2.2 Modelo: `ShareCommand` en `CreateTransactionCommand`; `ExpenseShare` y `ShareStatus` en
  `domain/model`; `sharedByUserId` y `shareId` en `Transaction` (y todos sus constructores, incluido
  el puente del asistente con `shares` `null`). Verifica la suite existente en verde.
- [x] 2.3 `AffiliationRepositoryPort.findAcceptedByUser(userId)`: id de la afiliación → el otro
  usuario, y su implementación en `AffiliationR2dbcAdapter`. Verifica `bruno/shares/` 1.3.
- [x] 2.4 `TransactionBatchValidator`: resuelve cada parte contra las afiliaciones aceptadas
  (`[i].shares[j].affiliationId`), exige categoría de gasto en la transferencia compartida y la
  convierte en `EXPENSE`. Verifica `CreateTransactionsUseCaseTest` (afiliación desvinculada,
  inexistente, transferencia a gasto, categoría de ingreso).
- [x] 2.5 `CreateTransactionsUseCase`: arma las partes y los pendientes ajenos (sin cuenta ni
  categoría, `sharedByUserId`, `shareId`, grupo propio cada uno, en la moneda en que llegó el monto
  del elemento) y los entrega al puerto; el saldo y las tasas de hoy siguen sobre lo propio.
  Verifica `CreateTransactionsUseCaseTest` (un gasto con dos partes, en USD, con fecha futura,
  transferencia a dos personas).
- [x] 2.6 `TransactionRepositoryPort.saveAll` con las partes y los pendientes ajenos, insertados en
  una transacción y en el orden movimientos → partes → pendientes; `TransactionR2dbcAdapter` escribe
  `shared_by_user_id` y `share_id` y los lee en `toDomain`. Verifica `bruno/shares/` 1.2.

## 3. Lo compartido en la respuesta

Skills: `java-architect`.

- [x] 3.1 Puerto de entrada que describe lo compartido de una lista de movimientos del usuario
  (`sharedBy`, `sharedFrom` en la moneda de la parte, `shares` con persona), su caso de uso y un
  puerto de salida con dos consultas fijas (lo recibido y las partes), implementado en un adapter R2DBC nuevo. Verifica el test
  del caso de uso con mocks y `bruno/shares/` 1.2.
- [x] 3.2 `TransactionResponse`: `accountId` admite `null`; `sharedBy`, `sharedFrom` y `shares`.
  `TransactionController` consulta el puerto en el alta, el `PATCH`, la lista de pendientes, la
  aprobación y la confirmación de grupo. Verifica `TransactionControllerTest` y `TransactionsIT`.
- [x] 3.3 `TransactionReportResponse` y `TransactionReportController` con los tres campos. Verifica
  `TransactionReportControllerTest` y `bruno/shares/reporte-de-a.yml`.

## 4. Aprobar y rechazar

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `ApprovePendingRequest` (`accountId`, `categoryId`, opcionales) como cuerpo opcional de
  `POST /transactions/{id}/approve`, y `ApprovePendingTransactionPort.approve` con esos dos valores.
  Verifica `TransactionControllerTest` (sin cuerpo sigue funcionando; con cuerpo lo pasa).
- [x] 4.2 `ApprovePendingTransactionUseCase`: sin cuenta exige el cuerpo (400 en cada campo que
  falte); con cuenta lo prohíbe (400 en el que sobre); valida las referencias con
  `ReferenciasDelUsuario` y el tipo de la categoría; convierte con la tasa de la fecha del pendiente
  si la cuenta está en otra moneda (`original`); valida el saldo sobre el monto convertido. Verifica
  `ApprovePendingTransactionUseCaseTest`.
- [x] 4.3 Pagar la parte: si el pendiente tiene `shareId`, el puerto confirma el pendiente con su
  cuenta, categoría y monto, pasa la parte a `PAID` y crea el ingreso pendiente de quien pagó (fecha
  de la aprobación, grupo propio), todo en una transacción y condicionado a `PENDING` (404 si no).
  Verifica `ApprovePendingTransactionUseCaseTest` y `bruno/shares/` 1.4 y 1.7.
- [x] 4.4 `AprobacionDePendientes.exigirGrupo`: `INVALID_STATE` en `[i].accountId` para un pendiente
  sin cuenta, antes del saldo. Verifica `AprobacionDePendientesTest` y `ApprovePendingGroupUseCaseTest`.
- [x] 4.5 Rechazar la parte, uno a uno y en grupo: las escrituras de borrado de pendientes del adapter
  (`deletePending`, `deletePendingIn`) pasan a `REJECTED` la parte del pendiente que borran, en la
  misma transacción. Así los dos caminos de rechazo la cumplen sin que los casos de uso cambien. Sin
  base en la suite, lo verifica `bruno/shares/rechazar-parte-c.yml` y `reporte-de-a.yml`.

## 5. Modificar y borrar

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `UpdateTransactionUseCase` y `DeleteTransactionUseCase`: 409 `INVALID_STATE` en `status`
  sobre un pendiente con `sharedByUserId`. Verifica sus tests y `bruno/shares/` 1.5.
- [x] 5.2 `UpdateTransactionUseCase`: 409 en `amount` o `type` si el gasto tiene partes `PENDING`
  (puerto que lo consulta). Verifica `UpdateTransactionUseCaseTest` y `bruno/shares/` 1.5.
- [x] 5.3 Borrar el gasto de quien pagó por `DELETE`, por rechazo uno a uno y por descarte de grupo:
  las tres escrituras de borrado del adapter (`deleteByIdAndUser`, `deletePending`,
  `deletePendingIn`) anulan sus partes `PENDING` y borran sus pendientes en la misma transacción, y la
  deshacen si el borrado no afectó la fila. Lo verifica `bruno/shares/` 1.6 y 1.7.

## 6. Series y compras en cuotas

Skills: `java-architect`.

- [x] 6.1 `shares` en `CreateRecurrenceRequest` y en el request de compra en cuotas (alta y
  simulación), con una constraint que solo acepta ausente, `null` o vacío. Verifica
  `CreateRecurrenceRequestTest`, `CreateInstallmentPurchaseRequestTest` y `bruno/` 1.10.

## 7. Documentación

- [x] 7.1 `docs/api/contrato-api.md`: `shares` en el elemento del alta y sus errores, la transferencia
  compartida (y su precio: decisión 4 del design), la aprobación con cuerpo y su tabla de errores, los
  campos `sharedBy`, `sharedFrom` y `shares` en la respuesta y en `reports/transactions`, `accountId`
  `null` en un pendiente compartido, el 409 de `PATCH`, `DELETE` y grupo, `shares` en series y
  cuotas. En «Movimientos pendientes», lo compartido como tercera vía. En «Lo que el API todavía no
  tiene»: sale «Crear movimientos pendientes por otra vía que el asistente»; entran «Cambiar las partes
  de un gasto compartido» y «Retirar una transferencia compartida».
- [x] 7.2 `docs/database/modelo-datos.md`, sección de finanzas compartidas: qué escribe cada acción
  (tabla de la decisión 8) y que el gasto se edita salvo en monto y tipo.
- [x] 7.3 Replicar en `bruno-personal/` (ruta, cuerpo, autenticación, sin ejecutar):
  `movimientos/registrar-compartido.yml` y `movimientos/aprobar-compartido.yml`, y `aprobar.yml`
  remite al segundo. La serie y la compra con partes no se replican: en esa colección, que se usa
  con datos reales, serían requests que solo fallan.

## 8. Verificación

- [x] 8.1 `.\gradlew.bat build` en verde: 1411 tests, 0 fallos, 0 errores, 0 omitidos; cobertura de línea
  98,54 %.
- [x] 8.2 `verificar-bruno.ps1 -RecargarDatos` en verde: 824 requests (822 pasan y 2 omitidos), 597/597 tests,
  1634/1634 aserciones, y `check-saldos.sql` con las 65 cuentas cuadradas. Los 2 omitidos son de
  FIN-58 (`assistant/confirmar-categoria-borrada.yml` y `assistant/grupo-sin-saldo-confirmar.yml`):
  `bru` no los puede parsear por un valor sin comillas, y van a un issue aparte.
