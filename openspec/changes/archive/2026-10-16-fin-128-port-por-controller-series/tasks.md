# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de los cuatro `*Recurrence*UseCaseTest`,
  los cinco `*InstallmentPurchase*UseCaseTest`, `CatchUpSeriesUseCaseTest`, `RecurrenceControllerTest`,
  `InstallmentPurchaseControllerTest`, `RecurrencesIT`, `InstallmentPurchasesIT` y
  `SeriesDelAsistenteAdapterTest`; verificación: los números quedan escritos en esta casilla
  - Total 1738. Series: Create 11, List 3, Update 17, Cancel 2 (33). Cuotas: Preview 16, Create 7,
    List 1, Update 6, Cancel 2 (32). `CatchUpSeriesUseCaseTest` 2, `RecurrenceControllerTest` 11,
    `InstallmentPurchaseControllerTest` 13, `RecurrencesIT` 8, `InstallmentPurchasesIT` 10,
    `SeriesDelAsistenteAdapterTest` 2.

## 2. Port y use case de series

Skills: `java-architect`, `java-testing`.

- [x] 2.1 Unir `CreateRecurrenceUseCaseTest`, `ListRecurrencesUseCaseTest`,
  `UpdateRecurrenceUseCaseTest` y `CancelRecurrenceUseCaseTest` en `RecurrenceUseCaseTest` con
  `@Nested` `Crear`, `Listar`, `Actualizar` y `Cancelar`, mocks y `useCase()` en la clase externa,
  sin `@MockitoSettings` y con `lenient()` en los stubs de escenario de los tres `@Nested` que venían
  de un test `LENIENT`; apuntar a `RecurrenceUseCase` y verlo fallar por no compilar
- [x] 2.2 Crear `RecurrencePort` con `create`, `list`, `update` y `cancel` (mismas firmas y javadoc) y
  `RecurrenceUseCase` con la lógica de los cuatro use cases y sus auxiliares privados; verificación:
  `RecurrenceUseCaseTest` en verde con el mismo número de casos que la línea base
- [x] 2.3 `RecurrenceControllerTest` con un solo `@MockitoBean RecurrencePort`; verlo fallar por no
  compilar, y después `RecurrenceController` depende solo de `RecurrencePort`; verificación:
  `RecurrenceControllerTest` y `RecurrencesIT` en verde con los mismos casos

## 3. Port y use case de compras en cuotas

Skills: `java-architect`, `java-testing`.

- [x] 3.1 Unir `PreviewInstallmentPurchaseUseCaseTest`, `CreateInstallmentPurchaseUseCaseTest`,
  `ListInstallmentPurchasesUseCaseTest`, `UpdateInstallmentPurchaseUseCaseTest` y
  `CancelInstallmentPurchaseUseCaseTest` en `InstallmentPurchaseUseCaseTest` con `@Nested`
  `Simular`, `Crear`, `Listar`, `Actualizar` y `Cancelar`, con el mismo criterio de estrictez que 2.1;
  apuntar a `InstallmentPurchaseUseCase` y verlo fallar por no compilar
- [x] 3.2 Crear `InstallmentPurchasePort` con `preview`, `create`, `list`, `update` y `cancel` (mismas
  firmas y javadoc) e `InstallmentPurchaseUseCase` con la lógica de los cinco use cases, sus
  auxiliares privados y `PlanDeCompra` como record privado anidado; verificación:
  `InstallmentPurchaseUseCaseTest` en verde con el mismo número de casos que la línea base
- [x] 3.3 `InstallmentPurchaseControllerTest` con un solo `@MockitoBean InstallmentPurchasePort`; verlo
  fallar por no compilar, y después `InstallmentPurchaseController` depende solo de
  `InstallmentPurchasePort`; verificación: `InstallmentPurchaseControllerTest` e
  `InstallmentPurchasesIT` en verde con los mismos casos

## 4. Limpieza

- [x] 4.1 Borrar los nueve ports por operación, los nueve use cases, `PlanDeCompra.java` y los nueve
  tests de origen; verificación:
  `grep -rnE "(Create|List|Update|Cancel)Recurrences?(Port|UseCase)|(Preview|Create|List|Update|Cancel)InstallmentPurchases?(Port|UseCase)|PlanDeCompra\.java" src`
  sin resultados, y `CatchUpSeriesUseCaseTest` y `SeriesDelAsistenteAdapterTest` en verde con los
  mismos casos

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base y los
  umbrales de JaCoCo sin tocar
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
