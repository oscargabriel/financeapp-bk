# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de `GetTransactionReportUseCaseTest`,
  `GetBalanceUseCaseTest`, `TransactionReportControllerTest`, `TransactionReportIT`,
  `TransactionReportTest`, `DescribeSharingUseCaseTest` y `TransactionControllerTest`; verificación:
  los números quedan escritos en esta casilla
  - Total 1739, 0 fallos. `GetTransactionReportUseCaseTest` 6, `GetBalanceUseCaseTest` 8,
    `TransactionReportControllerTest` 28, `TransactionReportIT` 7, `TransactionReportTest` 15,
    `DescribeSharingUseCaseTest` 2, `TransactionControllerTest` 64.

## 2. Port y use case

Skills: `java-architect`, `java-exceptions`, `java-testing`.

- [x] 2.1 Unir los dos tests de use case en `TransactionReportUseCaseTest` con `@Nested`
  `Transacciones` y `Balance`; mocks (`reportes`, `saldos`, `accounts`, `alDia`), `@Captor`, el stub
  `lenient()` de `alDia` y `useCase(Clock)` en la clase externa, estricta; cada `@Nested` construye
  el use case en su `@BeforeEach` con su reloj de origen (`AHORA` en UTC, `RELOJ` en Bogotá); cada
  caso llama a `transactions(...)` o `balance(...)`. Apuntar a `TransactionReportUseCase` y verlo
  fallar por no compilar
- [x] 2.2 Crear `TransactionReportPort` con `transactions` y `balance` (mismos parámetros y retornos
  que hoy, javadoc de cada origen) y `TransactionReportUseCase` con la lógica de los dos use cases,
  las dependencias con los nombres de `design.md` y `filtro`, `rango`, `invalido` y `Rango` privados,
  con los mismos mensajes, códigos y campos; verificación: `TransactionReportUseCaseTest` en verde con
  los casos de la línea base

## 3. Controller

Skills: `java-architect`, `java-testing`.

- [x] 3.1 `TransactionReportControllerTest` con `@MockitoBean` de `TransactionReportPort` y
  `DescribeSharingPort` en lugar de los tres, mismos casos; verlo fallar
- [x] 3.2 `TransactionReportController` depende solo de `TransactionReportPort` y
  `DescribeSharingPort`; verificación: `TransactionReportControllerTest` y `TransactionReportIT` en
  verde con los mismos casos

## 4. Limpieza

- [x] 4.1 Borrar `GetTransactionReportPort`, `GetBalancePort`, `GetTransactionReportUseCase`,
  `GetBalanceUseCase` y sus dos tests; verificación:
  `grep -rnE "(GetTransactionReport|GetBalance)(Port|UseCase)\b" src docs openspec/specs` sin
  resultados, y `DescribeSharingUseCaseTest`, `TransactionReportTest` y `TransactionControllerTest`
  en verde con los mismos casos

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base y los
  umbrales de JaCoCo sin tocar
  - 1739 tests, 0 fallos; JaCoCo 99,0 % línea y 93,6 % rama.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
  - Bruno: 1043 requests, 695 tests y 2078 assertions en verde, sin omitidos; `check-saldos.sql`: las
    83 cuentas cuadran. `git diff --stat dev -- docs/api bruno` vacío.

## Workflow follow-up

- Archivar el change en la misma rama antes del PR (paso 7 de `tareas-jira`).
