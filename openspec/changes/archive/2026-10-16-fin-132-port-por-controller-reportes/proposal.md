# Proposal

Origen: FIN-132, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas y siguieron FIN-127 a FIN-131. Es el último
controller de varios ports del core; FIN-98 cambia el reporte después de esta tarea.

## Why

`TransactionReportController` depende de tres ports de entrada: `GetTransactionReportPort` y
`GetBalancePort`, uno por endpoint, cada uno con su use case de una sola función pública, y
`DescribeSharingPort`. `AGENTS.md` pide un solo `<Recurso>Port` por controller, con una operación por
endpoint, implementado por un solo `<Recurso>UseCase`, y deja aparte el use case que usan otros
adapters. FIN-131 dejó `DescribeSharingPort` aparte porque lo comparten los dos controllers de
movimientos.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros sigue aparte
  (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `TransactionReportPort` (nuevo, `core/domain/port/in/`) con una operación por endpoint, nombrada
  como el método del controller: `transactions` y `balance`, con los parámetros, retornos y javadoc
  de hoy. **Se borran** `GetTransactionReportPort` y `GetBalancePort`.
- `TransactionReportUseCase` (nuevo, `core/application/usecase/`) implementa `TransactionReportPort`
  con la lógica de los dos use cases que reemplaza; sus auxiliares (`filtro`, `rango`, `invalido`,
  `Rango`) pasan a privados. **Se borran** `GetTransactionReportUseCase` y `GetBalanceUseCase`.
- `TransactionReportController` depende de `TransactionReportPort` y de `DescribeSharingPort`, que
  sigue aparte (ver `design.md`).
- Los tests de los dos use cases se unen en `TransactionReportUseCaseTest` (un `@Nested` por
  operación), con los mismos casos; `TransactionReportControllerTest` mockea los dos ports.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: `GET /api/reports/transactions` y `GET /api/reports/balance` responden igual (cuerpos,
códigos y errores). El change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (reportes) y
  `TransactionReportController`.
- Tests: `GetTransactionReportUseCaseTest` y `GetBalanceUseCaseTest`, que se unen, y
  `TransactionReportControllerTest`. `TransactionReportIT`, `TransactionReportTest`,
  `DescribeSharingUseCaseTest` y `TransactionControllerTest` no cambian y siguen en verde.
- Siguen igual: `DescribeSharingPort`, `DescribeSharingUseCase`, `SeriesAlDia`,
  `TransactionReportQueryPort`, `BalanceQueryPort` y sus adapters.
- `docs/api/`, `bruno/`, `bruno-personal/`, `docs/` y `AGENTS.md` no cambian: ninguno nombra las
  clases que se borran.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- El monto original en el reporte: lo agrega FIN-98, sobre `TransactionReportPort`.
- Mover `DescribeSharingPort` dentro de un port de controller.
- Los controllers de un solo port y los ports de salida.
