# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código (el código es aún el de `dev`), correr `.\gradlew.bat test` y anotar aquí el
  total de tests, y los de `*AccountUseCaseTest` y `AccountControllerTest` según
  `build/test-results/test/*.xml`; verificación: los tres números quedan escritos en esta casilla.
  Línea base: 1734 tests en total; 52 en los use cases de cuentas (`ListAccountsUseCaseTest` 4,
  `CreateAccountUseCaseTest` 10, `UpdateAccountUseCaseTest` 26, `DeleteAccountUseCaseTest` 12);
  32 en `AccountControllerTest`; 9 en `AccountsIT`

## 2. Port y use case de cuentas

Skills: `java-architect`, `java-testing`.

- [x] 2.1 Unir los cuatro tests en `AccountUseCaseTest` con `@Nested` `Listar`, `Crear`,
  `Actualizar` y `Borrar`, mocks y `useCase()` en la clase externa, `lenient()` en los stubs de
  `altaPosible()` y sin `@MockitoSettings`; apuntar a `AccountUseCase` y verlo fallar por no compilar
- [x] 2.2 Crear `AccountPort` con `list`, `create`, `update` y `delete` (mismas firmas y javadoc) y
  `AccountUseCase` con la lógica de los cuatro use cases en privados, `seriesActivas` estático de
  paquete y `monedaActiva` reutilizado en `monedaCambiable`; verificación: `AccountUseCaseTest` en
  verde con el mismo número de casos que la línea base
- [x] 2.3 Borrar los cuatro ports por operación, los cuatro `*AccountUseCase` y sus tests; apuntar
  `DeleteCategoryUseCase` a `AccountUseCase.seriesActivas` y el javadoc de `SaldoInicialSegunElTipo`
  a `AccountUseCase`; verificación: `DeleteCategoryUseCaseTest` en verde y
  `grep -rn "ListAccountsPort\|CreateAccountPort\|UpdateAccountPort\|DeleteAccountPort\|ListAccountsUseCase\|CreateAccountUseCase\|UpdateAccountUseCase\|DeleteAccountUseCase" src`
  sin resultados

## 3. Controller

Skills: `java-architect`, `java-testing`.

- [x] 3.1 `AccountControllerTest` con un solo `@MockitoBean AccountPort` en lugar de los cuatro;
  verlo fallar por no compilar
- [x] 3.2 `AccountController` depende solo de `AccountPort`; verificación: `AccountControllerTest` y
  `AccountsIT` en verde, con los mismos casos que la línea base

## 4. AGENTS.md

- [x] 4.1 Reemplazar el párrafo *El cableado que importa* de *Capas dentro de cada módulo* con la
  regla de `design.md` (*La regla en `AGENTS.md`*): nombre del port y del use case según el
  controller, cuándo un use case sigue aparte (`ResolveExchangeRatePort`, `CatchUpSeriesPort`), qué
  se toca para un endpoint nuevo en un controller migrado y en uno sin migrar, que los controllers de
  un solo port no se fusionan ni se renombran, que los ports de salida no cambian y que durante la
  migración conviven los dos patrones; conservar la excepción del enum. Sin claves de tablero en el
  texto nuevo; verificación: releer la sección contra los cinco puntos del primer criterio de FIN-126

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base y los
  umbrales de JaCoCo sin tocar
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
