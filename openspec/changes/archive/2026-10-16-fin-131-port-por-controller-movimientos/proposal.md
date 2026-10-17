# Proposal

Origen: FIN-131, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas y siguieron FIN-127 a FIN-130.

## Why

`TransactionController` depende de diez ports de entrada, cada uno con su use case de una sola
función pública: veinte clases para un controller de nueve endpoints. `AGENTS.md` ya pide un solo
`<Recurso>Port` por controller, con una operación por endpoint, implementado por un solo
`<Recurso>UseCase`, y deja aparte el use case con lógica propia que usan otros use cases o adapters.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros sigue aparte,
  como `ResolveExchangeRatePort` (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `TransactionPort` (nuevo, `core/domain/port/in/`) con una operación por endpoint, nombrada como el
  método del controller: `create`, `update`, `delete`, `pending`, `approve`, `reject`, `rejectBatch`,
  `approveGroup` y `rejectGroup`, con los parámetros, retornos y javadoc de hoy. **Se borran**
  `UpdateTransactionPort`, `DeleteTransactionPort`, `ListPendingTransactionsPort`,
  `ApprovePendingTransactionPort`, `RejectPendingTransactionPort`, `RejectPendingBatchPort`,
  `ApprovePendingGroupPort` y `RejectPendingGroupPort`.
- `TransactionUseCase` (nuevo, `core/application/usecase/`) implementa `TransactionPort` con la
  lógica de los ocho use cases que reemplaza. `create` delega en `CreateTransactionsPort`, que sigue
  aparte (ver `design.md`). Los auxiliares pasan a métodos privados, incluidos los de
  `PendienteDelUsuario` y `GrupoDelUsuario`, que se borran. **Se borran** `UpdateTransactionUseCase`,
  `DeleteTransactionUseCase`, `ListPendingTransactionsUseCase`, `ApprovePendingTransactionUseCase`,
  `RejectPendingTransactionUseCase`, `RejectPendingBatchUseCase`, `ApprovePendingGroupUseCase` y
  `RejectPendingGroupUseCase`.
- `TransactionController` depende de `TransactionPort` y de `DescribeSharingPort`, que sigue aparte
  porque lo comparte con `TransactionReportController` (ver `design.md`).
- Los tests de los ocho use cases se unen en `TransactionUseCaseTest` (un `@Nested` por operación),
  con los mismos casos más uno de la delegación de `create`; `TransactionControllerTest` mockea los
  dos ports.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/transactions` responden igual (cuerpos, códigos y errores). El
change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (movimientos) y
  `TransactionController`.
- Tests: los ocho tests de use case que se unen y `TransactionControllerTest`. `TransactionsIT`,
  `CreateTransactionsUseCaseTest`, `DescribeSharingUseCaseTest`, `AprobacionDePendientesTest`,
  `SaldoNoNegativoTest` y `PendienteDelAsistenteAdapterTest` no cambian y siguen en verde.
- Siguen igual: `CreateTransactionsPort`, `CreateTransactionsUseCase`, `TransactionBatchValidator`,
  `DescribeSharingPort`, `DescribeSharingUseCase`, `AprobacionDePendientes`, `SaldoNoNegativo`,
  `ReferenciasDelUsuario` y `PendienteDelAsistenteAdapter`, que sigue registrando lo del asistente
  por `CreateTransactionsPort`.
- `docs/database/demo-data.sql`: un comentario nombra `ApprovePendingTransactionUseCase` y pasa a
  nombrar `TransactionUseCase`. Es un comentario de SQL: no cambia los datos.
- `docs/api/`, `bruno/`, `bruno-personal/` y `AGENTS.md` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- `TransactionReportController` y su `DescribeSharingPort`: los migra FIN-132.
- Los controllers de un solo port y los ports de salida.
