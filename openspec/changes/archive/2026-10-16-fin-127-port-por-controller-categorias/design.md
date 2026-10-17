# Design

## Context

Ver proposal.md, *Why*. Hoy `CategoryController` inyecta `ListCategoriesPort`, `CreateCategoryPort`,
`UpdateCategoryPort` y `DeleteCategoryPort`, y `CatalogController` inyecta `ListCurrenciesPort` y el
mismo `ListCategoriesPort`, para que `GET /catalogs/categories` filtre y ordene igual que
`GET /categories` por construcción. Entre los cinco use cases usan `CategoryQueryPort`,
`CategoryRepositoryPort`, `RecurrenceRepositoryPort`, `CurrencyQueryPort` y `Clock`.
`DeleteCategoryUseCase` toma `noEncontrada()` de `UpdateCategoryUseCase` y `seriesActivas()` de
`AccountUseCase`.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 tal cual en `CategoryController`.
- Resolver `CatalogController`, que comparte un port con otro controller, sin perder que los dos
  listados de categorías coincidan.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.

## Decisions

### `CategoryUseCase`: una clase con los cuatro use cases, como `AccountUseCase`

Las cuatro operaciones públicas quedan como están, y sus auxiliares pasan a privados.
`noEncontrada()` deja de ser de paquete: su único consumidor externo era `DeleteCategoryUseCase`.

### `CatalogController` depende de un `CatalogPort` propio

`CatalogPort` tiene `listCurrencies()` y `listCategories(userId, appliesTo)`, y lo implementa
`CatalogUseCase` con `CurrencyQueryPort` y `CategoryQueryPort`.

- Descartado: que `CatalogController` dependa de `CategoryPort` y de un port de monedas. Son dos
  ports, y la regla pide uno por controller.
- Descartado: que `CatalogUseCase` consuma `CategoryPort`. Le daría acceso a crear, editar y borrar
  categorías para usar solo `list`, y `CategoryPort` es el port de un controller, no un use case
  con lógica propia de los que la regla deja aparte.

### El filtro por alcance pasa a `CategoryScope`

`ListCategoriesUseCase` traduce `appliesTo` en el conjunto de alcances que pide a
`CategoryQueryPort`: todos si viene `null`, y `compatibles()` si no. Para que `CategoryUseCase` y
`CatalogUseCase` lo hagan igual sin copiarlo, pasa a un estático
`CategoryScope.compatiblesCon(CategoryScope appliesTo)`, junto al `compatibles()` que ya existe. Los
dos use cases llaman a la misma consulta con el mismo conjunto, así que filtro y orden siguen
coincidiendo por construcción.

- Descartado: copiar el método privado en los dos use cases. Es lo que el javadoc de
  `CatalogController` evitaba con el port compartido.

### `seriesActivas` se queda en `AccountUseCase`

`CategoryUseCase` lo usa como hoy lo usa `DeleteCategoryUseCase`. FIN-126 dejó la decisión para
aquí: es un formateo de una línea con un solo consumidor externo, y moverlo a una clase propia
agrega una clase, que es lo contrario de lo que busca el epic.

### Tests

`CategoryUseCaseTest` une los cuatro tests de use case con `@Nested` `Listar`, `Crear`,
`Actualizar` y `Borrar`, mocks y `useCase()` en la clase externa, como `AccountUseCaseTest`. Ninguno
de los cuatro corre hoy con `@MockitoSettings`, así que el unido queda estricto sin `lenient()`.
`CatalogUseCaseTest` recibe el test de `ListCurrenciesUseCaseTest` y dos nuevos para
`listCategories` (sin `appliesTo` y con uno), que cubren la operación nueva. `CategoryScopeTest`
suma el caso `null` de `compatiblesCon`.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → se toma el conteo de `dev` antes de empezar y se compara al
  final: el total sube solo por los tests nuevos de `CatalogUseCaseTest` y `CategoryScopeTest`.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
