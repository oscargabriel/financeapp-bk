# Proposal

Origen: FIN-126, del epic FIN-74 (Calidad y mantenimiento). Abre la migración de los ports de
entrada por controller; FIN-127 a FIN-132 y FIN-90 esperan a este change.

## Why

`AccountController` depende de cuatro ports de entrada y cada uno baja a su propio use case con una
sola función pública: ocho clases para un recurso, difíciles de navegar, y el mismo patrón se repite
en casi todos los controllers del core. Antes de migrar los demás hay que dejar escrita la regla
nueva en `AGENTS.md` y aplicarla en un controller, para que las tareas siguientes la copien.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros use cases o
  adapters sigue aparte, como `ResolveExchangeRatePort` o `CatchUpSeriesPort`. Reemplaza la regla de
  un port y un use case por operación (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta; FIN-126 deja la regla en
  `AGENTS.md` y bloquea al resto (16-10-2026).
- `AGENTS.md` guarda reglas, no historia: el texto nuevo no lleva claves ni el porqué, que queda en
  este change (14-10-2026).

## What Changes

- `AGENTS.md`, sección *Capas dentro de cada módulo*: la regla de un port y un use case por
  controller reemplaza a la de uno por operación. Dice cómo se nombran el port y el use case, cuándo
  un use case sigue aparte, qué se toca para un endpoint nuevo, que los controllers de un solo port
  no se fusionan ni se renombran, y que mientras dure la migración conviven los migrados y los que
  no.
- `AccountPort` (nuevo, `core/domain/port/in/`) con `list`, `create`, `update` y `delete`, las
  mismas firmas de hoy. **Se borran** `ListAccountsPort`, `CreateAccountPort`, `UpdateAccountPort` y
  `DeleteAccountPort`.
- `AccountUseCase` (nuevo, `core/application/usecase/`) implementa `AccountPort` con la lógica de
  los cuatro use cases en métodos privados. **Se borran** `ListAccountsUseCase`,
  `CreateAccountUseCase`, `UpdateAccountUseCase` y `DeleteAccountUseCase`.
- `seriesActivas(long)` queda como estático de paquete en `AccountUseCase`, porque lo sigue usando
  `DeleteCategoryUseCase`.
- `AccountController` depende solo de `AccountPort`.
- Los cuatro tests de use case pasan a `AccountUseCaseTest`, uno `@Nested` por operación, con los
  mismos casos; `AccountControllerTest` mockea `AccountPort`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/accounts` responden igual (cuerpos, códigos y errores). El change
lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (cuentas y `DeleteCategoryUseCase`),
  `AccountController`, y el javadoc de `SaldoInicialSegunElTipo`, que nombra a
  `UpdateAccountUseCase`.
- Tests: los cuatro `*AccountUseCaseTest` y `AccountControllerTest`.
- Documentación: `AGENTS.md`. `docs/api/`, `bruno/` y `bruno-personal/` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- Los demás controllers con varios ports: cada uno tiene su tarea (FIN-127 a FIN-132).
- Los controllers de un solo port (`ExchangeRateController`, `HomeSummaryController`,
  `MonthlySpendingController`, `StatusController`, `AssistantController`,
  `TelegramWebhookController`): no se fusionan ni se renombran.
- Los ports de salida.
- Quitar las claves y la historia del resto de `AGENTS.md`: es FIN-90.
