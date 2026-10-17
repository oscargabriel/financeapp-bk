# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de los cuatro tests de use case de
  perfil (`GetUserProfile`, `UpdateUserProfile`, `ChangePassword`, `SetFirstPassword`), los seis de
  autenticación (`RegisterUser`, `Login`, `GoogleRegister`, `GoogleLogin`, `RefreshSession`,
  `Logout`), `AperturaDeSesionTest`, `UserControllerTest`, `AuthControllerTest`, `UsersIT` y
  `JwtSecurityIT`; verificación: los números quedan escritos en esta casilla
  - Total: 1738, sin fallos.
  - Perfil: `GetUserProfile` 2, `UpdateUserProfile` 21, `ChangePassword` 4, `SetFirstPassword` 15
    (42).
  - Autenticación: `RegisterUser` 15, `Login` 8, `GoogleRegister` 8, `GoogleLogin` 7,
    `RefreshSession` 3, `Logout` 1 (42).
  - `AperturaDeSesionTest` 2, `UserControllerTest` 28, `AuthControllerTest` 40, `UsersIT` 10,
    `JwtSecurityIT` 31.

## 2. Port y use case del perfil

Skills: `java-architect`, `java-testing`.

- [x] 2.1 Unir `GetUserProfileUseCaseTest`, `UpdateUserProfileUseCaseTest`,
  `ChangePasswordUseCaseTest` y `SetFirstPasswordUseCaseTest` en `UserUseCaseTest` con `@Nested`
  `Leer`, `Actualizar`, `CambiarContrasena` y `DefinirPrimeraContrasena`, mocks y `useCase()` en la
  clase externa, sin `@MockitoSettings` y con `lenient()` en los stubs de escenario del `@Nested`
  que viene de un test `LENIENT`; llamar a `changePassword` y `setFirstPassword`, apuntar a
  `UserUseCase` y verlo fallar por no compilar
- [x] 2.2 Crear `UserPort` con `get`, `update`, `changePassword` y `setFirstPassword` (mismos
  parámetros, retornos y javadoc) y `UserUseCase` con la lógica de los cuatro use cases, sus
  auxiliares privados y las fábricas de `ErroresDelPerfil` como métodos `private static`;
  verificación: `UserUseCaseTest` en verde con el mismo número de casos que la línea base
- [x] 2.3 `UserControllerTest` y `UsersIT` con un solo `@MockitoBean UserPort`; verlos fallar por no
  compilar, y después `UserController` depende solo de `UserPort`; verificación: `UserControllerTest`
  y `UsersIT` en verde con los mismos casos

## 3. Port y use case de la autenticación

Skills: `java-architect`, `java-testing`.

- [x] 3.1 Unir `RegisterUserUseCaseTest`, `LoginUseCaseTest`, `GoogleRegisterUseCaseTest`,
  `GoogleLoginUseCaseTest`, `RefreshSessionUseCaseTest` y `LogoutUseCaseTest` en `AuthUseCaseTest`
  con `@Nested` `Registrar`, `Login`, `RegistrarConGoogle`, `LoginConGoogle`, `Refrescar` y `Logout`,
  con el mismo criterio de estrictez que 2.1 y las fábricas del use case con `ReglasDelAlta`
  restringido y sin restringir; llamar a `googleRegister` y `googleLogin`, apuntar a `AuthUseCase` y
  verlo fallar por no compilar
- [x] 3.2 Crear `AuthPort` con `register`, `login`, `googleRegister`, `googleLogin`, `refresh` y
  `logout` (mismos parámetros, retornos y javadoc) y `AuthUseCase` con la lógica de los seis use
  cases, sus auxiliares privados y `tokenInvalido` de `ErroresDeGoogle` como método
  `private static`; recibe `ReglasDelAlta` y `AperturaDeSesion` por constructor; verificación:
  `AuthUseCaseTest` en verde con el mismo número de casos que la línea base
- [x] 3.3 `AuthControllerTest` con un solo `@MockitoBean AuthPort`; verlo fallar por no compilar, y
  después `AuthController` depende solo de `AuthPort`; verificación: `AuthControllerTest` y
  `JwtSecurityIT` en verde con los mismos casos

## 4. Limpieza

- [x] 4.1 Borrar los diez ports por operación, los diez use cases, `ErroresDelPerfil.java`,
  `ErroresDeGoogle.java` y los diez tests de origen; verificación:
  `grep -rnE "(GetUserProfile|UpdateUserProfile|ChangePassword|SetFirstPassword|RegisterUser|Login|GoogleRegister|GoogleLogin|RefreshSession|Logout)(Port|UseCase)\b|ErroresDe(lPerfil|Google)" src`
  sin resultados, y `AperturaDeSesionTest` en verde con los mismos casos

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base y los
  umbrales de JaCoCo sin tocar
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
