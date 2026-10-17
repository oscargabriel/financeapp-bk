# Proposal

Origen: FIN-128, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas y siguió FIN-127 con categorías.

## Why

`RecurrenceController` depende de cuatro ports de entrada e `InstallmentPurchaseController` de
cinco, cada uno con su use case de una sola función pública: dieciocho clases para dos controllers,
más `PlanDeCompra`, que existe solo para que la simulación y el alta de una compra compartan
validación y plan. `AGENTS.md` ya pide un solo `<Recurso>Port` por controller, implementado por un
solo `<Recurso>UseCase`.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros sigue aparte,
  como `CatchUpSeriesPort` (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `RecurrencePort` (nuevo, `core/domain/port/in/`) con `create`, `list`, `update` y `cancel`, las
  mismas firmas y javadoc de hoy. **Se borran** `CreateRecurrencePort`, `ListRecurrencesPort`,
  `UpdateRecurrencePort` y `CancelRecurrencePort`.
- `RecurrenceUseCase` (nuevo, `core/application/usecase/`) implementa `RecurrencePort` con la
  lógica de los cuatro use cases. Sus auxiliares (`preparar`, `validarCantidad`, `proxima`,
  `invalido`, `noEncontrada`, `vista`, `sinProxima`, `completar`) y los tipos `Nueva`, `Hecha` y
  `Edicion` quedan privados. **Se borran** `CreateRecurrenceUseCase`, `ListRecurrencesUseCase`,
  `UpdateRecurrenceUseCase` y `CancelRecurrenceUseCase`.
- `InstallmentPurchasePort` (nuevo) con `preview`, `create`, `list`, `update` y `cancel`;
  `InstallmentPurchaseUseCase` lo implementa con la lógica de los cinco use cases. **Se borran**
  `PreviewInstallmentPurchasePort`, `CreateInstallmentPurchasePort`,
  `ListInstallmentPurchasesPort`, `UpdateInstallmentPurchasePort` y
  `CancelInstallmentPurchasePort`, y sus cinco use cases.
- `PlanDeCompra` pasa a ser un record privado de `InstallmentPurchaseUseCase` y su archivo se borra
  (ver `design.md`).
- `RecurrenceController` depende solo de `RecurrencePort` e `InstallmentPurchaseController` solo de
  `InstallmentPurchasePort`.
- `CatchUpSeriesPort`, `CatchUpSeriesUseCase` y `SeriesAlDia` no cambian: los usa el asistente por
  `SeriesDelAsistenteAdapter` y los reportes, no estos controllers.
- Los tests de los use cases se unen en `RecurrenceUseCaseTest` e `InstallmentPurchaseUseCaseTest`
  (un `@Nested` por operación), con los mismos casos; los dos tests de controller mockean el port
  nuevo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/recurrences` y `/api/installment-purchases` responden igual
(cuerpos, códigos y errores). El change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (series y compras en cuotas),
  `RecurrenceController` e `InstallmentPurchaseController`.
- Tests: los nueve tests de use case de series y cuotas, `RecurrenceControllerTest` e
  `InstallmentPurchaseControllerTest`. `RecurrencesIT`, `InstallmentPurchasesIT` y
  `SeriesDelAsistenteAdapterTest` no cambian y siguen en verde.
- `docs/api/`, `bruno/`, `bruno-personal/` y `AGENTS.md` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- Los demás controllers con varios ports: usuarios y autenticación (FIN-129), afiliaciones
  (FIN-130), movimientos (FIN-131) y reportes (FIN-132).
- Los controllers de un solo port y los ports de salida.
- `CatchUpSeriesPort` y `SeriesAlDia`: siguen aparte por la regla del epic.
- `ReferenciasDelUsuario` y `ZonaDelUsuario`, que comparten series, cuotas y movimientos: no son
  ports ni use cases de estos controllers.
