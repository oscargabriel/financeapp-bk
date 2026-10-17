# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de `SendAffiliationRequestUseCaseTest`,
  `ListAffiliationsUseCaseTest`, `ChangeAffiliationUseCaseTest`, `ListAffiliateBalancesUseCaseTest`,
  `ListSharesWithAffiliateUseCaseTest`, `AffiliationControllerTest`, `AffiliationsIT` y
  `AffiliationActionTest`; verificación: los números quedan escritos en esta casilla
  - Total 1738 (0 fallos, 0 omitidos). `SendAffiliationRequestUseCaseTest` 5,
    `ListAffiliationsUseCaseTest` 1, `ChangeAffiliationUseCaseTest` 5,
    `ListAffiliateBalancesUseCaseTest` 1, `ListSharesWithAffiliateUseCaseTest` 6 (18 entre los cinco),
    `AffiliationControllerTest` 15, `AffiliationsIT` 16, `AffiliationActionTest` 25.

## 2. Port y use case

Skills: `java-architect`, `java-testing`.

- [x] 2.1 Unir los cinco tests de use case de afiliaciones en `AffiliationUseCaseTest` con `@Nested`
  `Enviar`, `Listar`, `CambiarEstado`, `Saldos` y `Partes`, mocks, `Clock` y construcción del use
  case en la clase externa; los casos de `CambiarEstado` llaman a `accept`, `unlink` o `cancel` según
  la acción que prueban, y los de saldos y partes a `balances` y `shares`; apuntar a
  `AffiliationUseCase` y verlo fallar por no compilar
- [x] 2.2 Crear `AffiliationPort` con `list`, `balances`, `shares`, `send`, `accept`, `reject`,
  `cancel` y `unlink` (mismos parámetros y retornos que hoy, javadoc de cada origen) y
  `AffiliationUseCase` con la lógica de los cinco use cases: las cuatro acciones delegan en un
  `cambiar(userId, affiliationId, action)` privado, y las fábricas de errores quedan como
  `solicitudNoEncontrada`, `accionInvalida`, `afiliacionNoEncontrada`, `sinPartes` y `duplicada`,
  con los mismos mensajes; verificación: `AffiliationUseCaseTest` en verde con el mismo número de
  casos que la línea base

## 3. Controller

Skills: `java-architect`, `java-testing`.

- [x] 3.1 `AffiliationControllerTest` con un solo `@MockitoBean AffiliationPort`; el parametrizado de
  `reject`, `cancel` y `unlink` pasa a `@MethodSource` con la ruta, el estado de destino y la
  operación del port, y el del id inválido verifica `verifyNoInteractions`; verlo fallar por no
  compilar
- [x] 3.2 `AffiliationController` depende solo de `AffiliationPort` y deja de importar
  `AffiliationAction`; verificación: `AffiliationControllerTest` y `AffiliationsIT` en verde con los
  mismos casos

## 4. Limpieza

- [x] 4.1 Borrar los cinco ports por operación, los cinco use cases y los cinco tests de origen;
  verificación:
  `grep -rnE "(SendAffiliationRequest|ListAffiliations|ChangeAffiliation|ListAffiliateBalances|ListSharesWithAffiliate)(Port|UseCase)\b" src`
  sin resultados, y `AffiliationActionTest` en verde con los mismos casos

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base y los
  umbrales de JaCoCo sin tocar
  - 1738 tests, 0 fallos. `AffiliationUseCaseTest` 18, `AffiliationControllerTest` 15,
    `AffiliationsIT` 16, `AffiliationActionTest` 25.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
  - Bruno: 1043 requests, 695/695 tests y 2078/2078 aserciones, sin omitidos; `check-saldos.sql`: las
    83 cuentas cuadran. Sin cambios en `docs/api/` ni `bruno/`.
