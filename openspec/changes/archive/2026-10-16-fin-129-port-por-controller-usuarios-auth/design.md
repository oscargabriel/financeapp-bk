# Design

## Context

Ver proposal.md, *Why*. Los use cases de perfil comparten los errores de `ErroresDelPerfil`, una
clase de paquete con fábricas estáticas que nadie más usa. Los de autenticación comparten tres
piezas:

- `ErroresDeGoogle`, igual que la anterior, con un solo error para el registro y el login con Google.
- `ReglasDelAlta` (`@Component`), la lista de admitidos y el correo libre del alta con contraseña y
  del registro con Google. Recibe `registro.admitidos.enabled` por `@Value`, y sus tests la
  construyen real con el flag en `true` o `false`.
- `AperturaDeSesion` (`@Component`), que abre la sesión de los tres logins (FIN-52, decisión 8).
  Recibe `spring.security.jwt.refresh-expiration` por `@Value` y tiene su propio
  `AperturaDeSesionTest`. `LoginUseCaseTest` la usa real; `GoogleLoginUseCaseTest` y
  `GoogleRegisterUseCaseTest` la mockean.

Ninguna de las cuatro tiene consumidores fuera de estos use cases.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 tal cual en los dos controllers.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.
- Tocar `ResolveExchangeRatePort` o los ports de salida.

## Decisions

### `AperturaDeSesion` y `ReglasDelAlta` siguen como componentes aparte

Las dos son el porqué de que el login con contraseña y los de Google respondan igual: una sola forma
de abrir la sesión, y una sola forma de admitir un correo. Cada una lleva su propia propiedad de
configuración, y `AperturaDeSesion` tiene un test propio que los logins dan por hecho («lo que
guarda la sesión lo prueba `AperturaDeSesionTest`»). `AuthUseCase` las recibe por constructor y
conserva `@AllArgsConstructor`.

- Descartado: llevarlas a métodos privados de `AuthUseCase`, como `PlanDeCompra` en FIN-128.
  `AuthUseCase` pasaría a constructor explícito, con nueve dependencias y dos `@Value`, y los tests
  de Google, que hoy mockean la apertura, tendrían que reescribir sus casos para comprobar la sesión
  a través de los mocks del emisor, el generador y el repositorio: el criterio de cubrir los mismos
  casos dejaría de ser verificable comparando tests. `PlanDeCompra` no tenía configuración ni test
  propio.

### Los errores compartidos pasan a métodos privados

`ErroresDelPerfil` y `ErroresDeGoogle` existían para que varios use cases construyeran el mismo
error. Con un use case por controller, cada una queda con un solo consumidor: sus fábricas pasan a
métodos `private static` de `UserUseCase` y `AuthUseCase`, con el mismo javadoc, y los dos archivos
se borran. `tokenDeGoogleRechazado` (400, perfil) y `tokenInvalido` (401, login) siguen separados:
son respuestas distintas a propósito (FIN-53, decisión 3).

### Nombres de las operaciones

Las operaciones conservan el nombre de su port de origen salvo cuando, dentro del port unido, dejan
de decir qué hacen o chocan con otra:

| Port de origen | Operación nueva |
|---|---|
| `GetUserProfilePort.get` | `UserPort.get` |
| `UpdateUserProfilePort.update` | `UserPort.update` |
| `ChangePasswordPort.change` | `UserPort.changePassword` |
| `SetFirstPasswordPort.define` | `UserPort.setFirstPassword` |
| `RegisterUserPort.register` | `AuthPort.register` |
| `LoginPort.login` | `AuthPort.login` |
| `GoogleRegisterPort.register` | `AuthPort.googleRegister` |
| `GoogleLoginPort.login` | `AuthPort.googleLogin` |
| `RefreshSessionPort.refresh` | `AuthPort.refresh` |
| `LogoutPort.logout` | `AuthPort.logout` |

- Descartado: sobrecargar `register` y `login` con los tipos de Google. Compila, pero
  `login(String)` junto a `refresh(String)` y `logout(String)` no dice que el `String` es un ID token
  de Google, y un `verify(port).login(any())` en los tests se vuelve ambiguo.

### Tests: un `@Nested` por operación y la estrictez de cada origen

`UserUseCaseTest` une los cuatro tests de perfil con `@Nested` `Leer`, `Actualizar`,
`CambiarContrasena` y `DefinirPrimeraContrasena`; `AuthUseCaseTest` une los seis de autenticación con
`Registrar`, `Login`, `RegistrarConGoogle`, `LoginConGoogle`, `Refrescar` y `Logout`. Los mocks y los
`useCase()` van en la clase externa, como `AccountUseCaseTest`. Los orígenes que construyen el use
case de dos formas (`ReglasDelAlta` con el flag en `true` o `false`) conservan las dos fábricas.

Seis de los diez tests de origen (`LoginUseCaseTest`, `GoogleLoginUseCaseTest`,
`GoogleRegisterUseCaseTest`, `RegisterUserUseCaseTest`, `RefreshSessionUseCaseTest` y
`UpdateUserProfileUseCaseTest`) corren con `@MockitoSettings(strictness = LENIENT)`. En el test
unido, la clase externa queda estricta y los stubs del `@BeforeEach` de esos `@Nested` llevan
`lenient()`, para que los casos que hoy son estrictos no pierdan el aviso de stubs sobrantes. Es el
criterio de FIN-126 y FIN-128.

- Descartado: `LENIENT` en la clase externa. Es el cambio más corto, pero relaja los tests que hoy
  son estrictos.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → se toma el conteo de la rama antes de empezar y se compara
  al final: los tests de use case unidos suman lo mismo que sus orígenes, y el total no cambia.
- [`AuthUseCase` con diez dependencias: siete ports, `Clock`, `ReglasDelAlta` y `AperturaDeSesion`] →
  es la unión de las de hoy, sin repetidas; se acepta a
  cambio de un solo use case por controller.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
