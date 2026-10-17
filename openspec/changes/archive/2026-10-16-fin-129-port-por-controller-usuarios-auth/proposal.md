# Proposal

Origen: FIN-129, del epic FIN-74 (Calidad y mantenimiento). Sigue la migración de los ports de
entrada por controller que abrió FIN-126 con cuentas y siguieron FIN-127 (categorías) y FIN-128
(series y compras en cuotas). Va antes de FIN-38 (epic FIN-6), que cambia el login.

## Why

`UserController` depende de cuatro ports de entrada y `AuthController` de seis, cada uno con su use
case de una sola función pública: veinte clases para dos controllers, más `ErroresDelPerfil` y
`ErroresDeGoogle`, que existen solo para que esos use cases compartan sus errores. `AGENTS.md` ya
pide un solo `<Recurso>Port` por controller, implementado por un solo `<Recurso>UseCase`.

Decisiones del epic que aplica:

- Los ports de entrada se agrupan por controller: si la combinación es directa, el controller
  depende de un solo `<Recurso>Port`, con una operación por endpoint, implementado por un solo
  `<Recurso>UseCase`. No es estricta: un use case con lógica propia que usan otros sigue aparte,
  como `ResolveExchangeRatePort` (16-10-2026).
- La migración va por controller, sin piloto y con prioridad alta (16-10-2026).
- Los ports de salida quedan como están (fuera de alcance, 16-10-2026).

## What Changes

- `UserPort` (nuevo, `core/domain/port/in/`) con `get`, `update`, `changePassword` y
  `setFirstPassword`, con los parámetros, retornos y javadoc de hoy. `change` y `define` toman el
  nombre del endpoint, porque en un port con cuatro operaciones ya no dicen qué cambian (ver
  `design.md`). **Se borran** `GetUserProfilePort`, `UpdateUserProfilePort`, `ChangePasswordPort` y
  `SetFirstPasswordPort`.
- `UserUseCase` (nuevo, `core/application/usecase/`) implementa `UserPort` con la lógica de los
  cuatro use cases; sus auxiliares y los errores de `ErroresDelPerfil` quedan como métodos privados.
  Sigue consumiendo `ResolveExchangeRatePort`. **Se borran** `GetUserProfileUseCase`,
  `UpdateUserProfileUseCase`, `ChangePasswordUseCase`, `SetFirstPasswordUseCase` y
  `ErroresDelPerfil`.
- `AuthPort` (nuevo) con `register`, `login`, `googleRegister`, `googleLogin`, `refresh` y
  `logout`. Las dos de Google llevan el prefijo para no quedar como sobrecargas de `register` y
  `login`. **Se borran** `RegisterUserPort`, `LoginPort`, `GoogleRegisterPort`, `GoogleLoginPort`,
  `RefreshSessionPort` y `LogoutPort`.
- `AuthUseCase` (nuevo) implementa `AuthPort` con la lógica de los seis use cases; sus auxiliares y
  el error de `ErroresDeGoogle` quedan como métodos privados. **Se borran** `RegisterUserUseCase`,
  `LoginUseCase`, `GoogleRegisterUseCase`, `GoogleLoginUseCase`, `RefreshSessionUseCase`,
  `LogoutUseCase` y `ErroresDeGoogle`.
- `AperturaDeSesion` y `ReglasDelAlta` siguen como componentes aparte, que `AuthUseCase` recibe por
  constructor (ver `design.md`).
- `UserController` depende solo de `UserPort` y `AuthController` solo de `AuthPort`.
- Los tests de los use cases se unen en `UserUseCaseTest` y `AuthUseCaseTest` (un `@Nested` por
  operación), con los mismos casos; `UserControllerTest`, `AuthControllerTest` y `UsersIT` mockean
  el port nuevo. `AperturaDeSesionTest` no cambia.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: los endpoints de `/api/users/me` y `/api/auth` responden igual (cuerpos, códigos, cookies y
errores). El change lleva `skip_specs: true`.

## Impact

- Código: `core/domain/port/in/`, `core/application/usecase/` (perfil y autenticación),
  `UserController` y `AuthController`.
- Tests: los diez tests de use case de perfil y autenticación, `UserControllerTest`,
  `AuthControllerTest` y `UsersIT`. `AperturaDeSesionTest` y `JwtSecurityIT`, que pide las rutas de
  `/api/auth` sin mockear sus ports, no cambian y siguen en verde.
- `docs/api/`, `bruno/`, `bruno-personal/` y `AGENTS.md` no cambian.
- Sin cambios de base, configuración ni despliegue.

## Fuera de alcance

- Los demás controllers con varios ports: afiliaciones (FIN-130), movimientos (FIN-131) y reportes
  (FIN-132).
- Los controllers de un solo port y los ports de salida.
- `ResolveExchangeRatePort`: sigue aparte por la regla del epic.
- Igualar el tiempo del login fallido: es FIN-38, que parte de lo que deja este change.
