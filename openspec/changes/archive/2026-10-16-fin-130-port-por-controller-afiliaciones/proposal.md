# Proposal

Origen: FIN-130, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas y siguieron FIN-127 (categorías), FIN-128
(series y compras en cuotas) y FIN-129 (usuarios y autenticación).

## Why

`AffiliationController` depende de cinco ports de entrada, cada uno con su use case de una sola
función pública: diez clases para un controller de ocho endpoints. `AGENTS.md` ya pide un solo
`<Recurso>Port` por controller, con una operación por endpoint, implementado por un solo
`<Recurso>UseCase`.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase` (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `AffiliationPort` (nuevo, `core/domain/port/in/`) con una operación por endpoint: `list`,
  `balances`, `shares`, `send`, `accept`, `reject`, `cancel` y `unlink`, con los parámetros, retornos
  y javadoc de hoy. Las cuatro acciones dejan de pasar por un `change(userId, id, action)` común (ver
  `design.md`). **Se borran** `SendAffiliationRequestPort`, `ListAffiliationsPort`,
  `ChangeAffiliationPort`, `ListAffiliateBalancesPort` y `ListSharesWithAffiliatePort`.
- `AffiliationUseCase` (nuevo, `core/application/usecase/`) implementa `AffiliationPort` con la
  lógica de los cinco use cases. La de cambiar el estado queda en un método privado que reciben las
  cuatro acciones con su `AffiliationAction`; los auxiliares y las fábricas de errores, como métodos
  privados, renombrados donde dos use cases usaban el mismo nombre. **Se borran**
  `SendAffiliationRequestUseCase`, `ListAffiliationsUseCase`, `ChangeAffiliationUseCase`,
  `ListAffiliateBalancesUseCase` y `ListSharesWithAffiliateUseCase`.
- `AffiliationController` depende solo de `AffiliationPort`.
- Los tests de los use cases se unen en `AffiliationUseCaseTest` (un `@Nested` por operación de
  origen), con los mismos casos; `AffiliationControllerTest` mockea el port nuevo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/affiliations` responden igual (cuerpos, códigos y errores). El
change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (afiliaciones) y
  `AffiliationController`.
- Tests: los cinco tests de use case de afiliaciones y `AffiliationControllerTest`.
  `AffiliationsIT`, que pide las rutas sin mockear ports, y `AffiliationActionTest` no cambian y
  siguen en verde.
- `AffiliationAction` sigue igual: el use case la usa para el estado de origen, el de destino y
  quién puede aplicarla.
- `docs/api/`, `bruno/`, `bruno-personal/` y `AGENTS.md` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- Los demás controllers con varios ports: movimientos (FIN-131) y reportes (FIN-132).
- Los controllers de un solo port y los ports de salida.
