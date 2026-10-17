# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de los cuatro `*Categor*UseCaseTest`,
  `ListCurrenciesUseCaseTest`, `CategoryScopeTest`, `CategoryControllerTest`,
  `CatalogControllerTest`, `CategoriesIT` y `CatalogsIT`; verificación: los números quedan escritos
  en esta casilla
  - Total 1734, 0 fallos, 0 omitidos. `ListCategoriesUseCaseTest` 5, `CreateCategoryUseCaseTest` 5,
    `UpdateCategoryUseCaseTest` 12, `DeleteCategoryUseCaseTest` 3 (25 entre los cuatro),
    `ListCurrenciesUseCaseTest` 1, `CategoryScopeTest` 3, `CategoryControllerTest` 28,
    `CatalogControllerTest` 13, `CategoriesIT` 8, `CatalogsIT` 3.

## 2. Filtro por alcance en el dominio

Skills: `java-architect`, `java-testing`.

- [x] 2.1 En `CategoryScopeTest`, casos para `CategoryScope.compatiblesCon`: `null` da los tres
  alcances y un alcance da sus `compatibles()`; verlo fallar por no compilar
- [x] 2.2 Agregar `compatiblesCon(CategoryScope)` estático a `CategoryScope`; verificación:
  `CategoryScopeTest` en verde

## 3. Port y use case de categorías

Skills: `java-architect`, `java-testing`.

- [x] 3.1 Unir `ListCategoriesUseCaseTest`, `CreateCategoryUseCaseTest`, `UpdateCategoryUseCaseTest`
  y `DeleteCategoryUseCaseTest` en `CategoryUseCaseTest` con `@Nested` `Listar`, `Crear`,
  `Actualizar` y `Borrar`, mocks y `useCase()` en la clase externa, sin `@MockitoSettings`; apuntar a
  `CategoryUseCase` y verlo fallar por no compilar
- [x] 3.2 Crear `CategoryPort` con `list`, `create`, `update` y `delete` (mismas firmas y javadoc) y
  `CategoryUseCase` con la lógica de los cuatro use cases en privados, `list` con
  `CategoryScope.compatiblesCon` y el mensaje de series con `AccountUseCase.seriesActivas`;
  verificación: `CategoryUseCaseTest` en verde con el mismo número de casos que la línea base
- [x] 3.3 `CategoryControllerTest` con un solo `@MockitoBean CategoryPort`; verlo fallar por no
  compilar, y después `CategoryController` depende solo de `CategoryPort`; verificación:
  `CategoryControllerTest` y `CategoriesIT` en verde con los mismos casos

## 4. Port y use case de catálogos

Skills: `java-architect`, `java-testing`.

- [x] 4.1 `CatalogUseCaseTest` con el test de `ListCurrenciesUseCaseTest` y dos de `listCategories`
  (sin `appliesTo` pide los tres alcances; con `EXPENSE` pide `EXPENSE` y `BOTH`); verlo fallar por
  no compilar
- [x] 4.2 Crear `CatalogPort` con `listCurrencies()` y `listCategories(userId, appliesTo)` y
  `CatalogUseCase` con `CurrencyQueryPort` y `CategoryQueryPort`; verificación: `CatalogUseCaseTest`
  en verde
- [x] 4.3 `CatalogControllerTest` con un solo `@MockitoBean CatalogPort`; verlo fallar por no
  compilar, y después `CatalogController` depende solo de `CatalogPort`, con su javadoc al día;
  verificación: `CatalogControllerTest` y `CatalogsIT` en verde con los mismos casos

## 5. Limpieza

- [x] 5.1 Borrar los cinco ports por operación, los cinco use cases y sus tests; verificación:
  `grep -rnE "(List|Create|Update|Delete)Categor(y|ies)(Port|UseCase)|ListCurrencies(Port|UseCase)" src`
  sin resultados

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base más los
  nuevos de las tareas 2.1 y 4.1, y los umbrales de JaCoCo sin tocar
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
  - 6.1: BUILD SUCCESSFUL, 1738 tests (1734 + 2 de `CategoryScopeTest` + 2 de `listCategories` en
    `CatalogUseCaseTest`), 0 fallos; `CategoryUseCaseTest` 25, `CategoryControllerTest` 28,
    `CatalogControllerTest` 13, `CategoriesIT` 8, `CatalogsIT` 3. JaCoCo 99,05 % línea, 93,63 % rama.
  - 6.2: Bruno PASS, 1043/1043 requests, 695/695 tests, 2078/2078 assertions; las 83 cuentas cuadran.
    `git diff --stat dev -- docs/api bruno` vacío.
