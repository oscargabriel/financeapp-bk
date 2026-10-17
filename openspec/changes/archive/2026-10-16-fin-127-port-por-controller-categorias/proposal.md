# Proposal

Origen: FIN-127, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas.

## Why

`CategoryController` depende de cuatro ports de entrada, cada uno con su use case de una sola
función pública, y `CatalogController` de dos más, uno de ellos compartido con el anterior: diez
clases para dos controllers. `AGENTS.md` ya pide un solo `<Recurso>Port` por controller,
implementado por un solo `<Recurso>UseCase`.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros sigue aparte
  (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `CategoryPort` (nuevo, `core/domain/port/in/`) con `list`, `create`, `update` y `delete`, las
  mismas firmas de hoy. **Se borran** `CreateCategoryPort`, `UpdateCategoryPort`,
  `DeleteCategoryPort` y `ListCategoriesPort`.
- `CategoryUseCase` (nuevo, `core/application/usecase/`) implementa `CategoryPort` con la lógica de
  los cuatro use cases; sus auxiliares (`nuevaCategoria`, `aplicar`, `alcanceCompatible`,
  `noEncontrada`) quedan privados. **Se borran** `CreateCategoryUseCase`, `UpdateCategoryUseCase`,
  `DeleteCategoryUseCase` y `ListCategoriesUseCase`.
- `CatalogPort` (nuevo) con `listCurrencies()` y `listCategories(userId, appliesTo)`, las dos
  operaciones de `/api/catalogs` que pasan por un port; `CatalogUseCase` lo implementa. **Se
  borran** `ListCurrenciesPort` y `ListCurrenciesUseCase`. `account-types` y `transaction-types`
  siguen leyendo el enum sin port, como dice `AGENTS.md`.
- La regla «sin `appliesTo` son todos los alcances; con él, los compatibles» pasa a un estático de
  `CategoryScope`, para que los dos use cases filtren igual por construcción (ver `design.md`).
- `CategoryController` depende solo de `CategoryPort` y `CatalogController` solo de `CatalogPort`.
- Los tests de los use cases se unen en `CategoryUseCaseTest` (un `@Nested` por operación) y
  `CatalogUseCaseTest`, con los mismos casos; los dos tests de controller mockean el port nuevo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/categories` y `/api/catalogs` responden igual (cuerpos, códigos y
errores). El change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (categorías y monedas),
  `core/domain/model/CategoryScope`, `CategoryController` y `CatalogController`.
- Tests: los cinco tests de use case de categorías y monedas, `CategoryScopeTest`,
  `CategoryControllerTest` y `CatalogControllerTest`.
- `docs/api/`, `bruno/`, `bruno-personal/` y `AGENTS.md` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- Los demás controllers con varios ports: series y cuotas (FIN-128), usuarios y autenticación
  (FIN-129), afiliaciones (FIN-130), movimientos (FIN-131) y reportes (FIN-132).
- Los controllers de un solo port y los ports de salida.
- La copia de `parseScope` entre `CategoryController` y `CatalogController`: no es un port ni un use
  case, y unirla es otro refactor.
