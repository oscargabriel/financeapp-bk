# Tasks

## 1. Esquema y datos

- [x] 1.1 `docs/database/update/20261012_01_refresh_tokens.sql`: columna `finance.users.token_version
  INTEGER NOT NULL DEFAULT 0` y tabla `finance.refresh_tokens` de la decisión 4 de design.md, con FK
  a `users` `ON DELETE CASCADE`, `token_hash CHAR(64)` único, índice por `family_id`, comentarios de
  tabla y columnas, y el bloque de reversión comentado. El encabezado dice que va **antes** del
  despliegue y que reinicia los datos locales (`cargar-datos-local.sql`). Verificar aplicándolo a la
  base local con `psql -v ON_ERROR_STOP=1`.
- [x] 1.2 Lo mismo en `schema.sql`, y la tabla en `docs/database/modelo-datos.md`. Verificar con la
  comparación de esquemas de ese documento: sin diferencias.
- [x] 1.3 `test-data.sql` siembra dos refresh con valor conocido y hash `encode(sha256(...), 'hex')`:
  uno vencido de `prueba@financeapp.local` y uno vigente de `inactivo@financeapp.local`. Verificar
  corriendo `cargar-datos-local.sql` dos veces seguidas sin error y consultando las dos filas.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 `bruno/auth/login.yml`: asserts de `refreshToken` no vacío y `refreshExpiresIn` 86400, y lo
  guarda en `refreshToken`. Requests nuevos en `bruno/auth/`, después del login:
  - `refresh.yml`: 200, par nuevo distinto, `expiresIn` 3600, `refreshExpiresIn` en (0, 86400];
    guarda el viejo en `refreshRotado` y el nuevo en `refreshToken`, y el access nuevo en
    `accessToken` (el resto de la colección sigue autenticando con él);
  - `refresh-reusado.yml`: `refreshRotado` da 401 `UNAUTHENTICATED` en `refreshToken`;
  - `refresh-tras-reuso.yml`: el reemplazo (`refreshToken`) da 401;
  - `login-segunda-sesion.yml`: login nuevo; guarda `accessToken` y `refreshToken`, y comprueba que
    el refresh difiere del de la primera sesión;
  - `refresh-inexistente.yml`, `refresh-vencido.yml` y `refresh-usuario-inactivo.yml` (los sembrados
    en 1.3): 401 `UNAUTHENTICATED` en `refreshToken`;
  - `refresh-sin-cuerpo.yml` y `logout-sin-cuerpo.yml`: 400 `VALIDATION_ERROR` en `refreshToken`;
  - `refresh-sin-credencial.yml`: 401 en `authorization` con `WWW-Authenticate: Basic`;
  - `refresh-con-bearer.yml`: con `accessToken` en vez de la Basic, 401 en `authorization`;
  - `logout-desconocido.yml`: 204;
  - `login-sesion-a-cerrar.yml`, `logout.yml` (204) y `refresh-tras-logout.yml` (401), con una
    tercera sesión propia para no tocar la que usa el resto de la colección.
- [x] 2.2 `bruno/users/`: `login-perfil.yml` guarda también `refreshPerfil`. Después de
  `cambiar-clave.yml`: `perfil-tras-cambiar-clave.yml` con `tokenPerfil` da 401 en `authorization`,
  y `refresh-tras-cambiar-clave.yml` con `refreshPerfil` da 401 en `refreshToken`. Después de
  `login-clave-nueva.yml` (que guarda su refresh), `refresh-clave-nueva.yml` da 200.
  `cambiar-clave-equivocada.yml` comprueba además que `tokenPerfil` sigue sirviendo.

  Hecho con un request aparte, `perfil-tras-clave-equivocada.yml`. En `auth/` se sumaron tres que
  el plan no listaba: `refresh-encadenado.yml` (el escenario «el refresh nuevo sirve para el
  siguiente refresco»), `logout-sin-credencial.yml` y `refresh-segunda-sesion.yml` (que el refresh
  sigue sirviendo tras los 401 de la cadena equivocada). Los de `users/` desde `cambiar-clave-invalida`
  se renumeraron.
- [x] 2.3 Verificar que fallan por la razón correcta: con el esquema del grupo 1 y la app de `dev`,
  `verificar-bruno.ps1 -Objetivo auth -RecargarDatos` y `-Objetivo users`. Anotar aquí qué pasa y qué
  falla.

  `auth`: 30 requests, 12 pasan y 18 fallan; 43 de 66 asserts. Pasan los 12 que ya existían. Los
  nuevos fallan porque las rutas no existen y caen en la cadena Bearer: 401 con
  `WWW-Authenticate: Bearer` y `field` `authorization`, o 404 `NOT_FOUND` con un Bearer válido. El
  login no trae `refreshExpiresIn`.

  `users`: 30 requests, 27 pasan y 3 fallan; 67 de 73 asserts. `perfil-tras-cambiar-clave` da 200
  (el token sigue sirviendo tras el cambio), y los dos refrescos dan el 401 de la cadena Bearer.
  `perfil-tras-clave-equivocada` ya pasa: hoy nada cierra la sesión.

## 3. El access depende de la sesión

Skills: `java-architect`, `java-security`.

- [x] 3.1 `compartido/domain/port/out/SesionDelTokenPort` y el decoder de `JwtConfig` que, tras la
  validación de Nimbus, exige el claim `sv` y `abierta(sub, sv)`; sin claim, `sub` no UUID o sesión
  cerrada, `BadJwtException`. Verificar con `JwtConfigTest` (acepta el token con sesión abierta;
  rechaza sin `sv`, con sesión cerrada y con `sub` inválido, sin consultar el puerto en los dos
  últimos que no lo necesitan).
- [x] 3.2 `TokenMother` firma con `sv`; agrega `sinVersionDeSesion()`. Configuración de test
  `compartido/support/SesionSiempreAbierta` para los slices que importan `JwtConfig`, y
  `@MockitoBean SesionDelTokenPort` en los `*IT` que usan `TokenMother`. Verificar con `gradlew test`
  en verde sin cambiar ningún assert existente.

  Los `*IT` importan la misma `SesionSiempreAbierta`, marcada `@Primary` para convivir con el
  adapter real: una línea por clase en vez de un mock y su `when` en cada `setUp`. Solo
  `JwtSecurityIT` declara el `@MockitoBean`, porque necesita cerrar la sesión. Suite: 1218 tests,
  0 fallos.
- [x] 3.3 `JwtSecurityIT`: sesión cerrada → 401 `UNAUTHENTICATED` en `authorization` con
  `WWW-Authenticate: Bearer`; token sin `sv` → 401.
- [x] 3.4 `SesionDelTokenR2dbcAdapter` en `core`: `EXISTS` sobre `users` activo, sin borrar y con
  `token_version` igual. Lo verifica Bruno (2.2, `perfil-tras-cambiar-clave`).
- [x] 3.5 `JwtTokenIssuerAdapter.issueFor(userId, tokenVersion)` emite `sv` y un `jti` aleatorio, para
  que dos access del mismo segundo no salgan iguales. Verificar con `JwtTokenIssuerAdapterTest`.
- [x] 3.6 `UserCredentials` gana `tokenVersion`, y `findActiveByEmail` la lee. `ACTUALIZAR_CLAVE` de
  `UserR2dbcAdapter` sube `token_version` en el mismo `UPDATE`. Verificar con `LoginUseCaseTest` y,
  para el SQL, con Bruno 2.2.

## 4. Login con refresh

Skills: `java-architect`.

- [x] 4.1 Modelo: `RefreshToken(value, expiresIn)` y `SessionTokens(access, refresh)`. Puertos de
  salida `RefreshTokenGeneratorPort` (`generar`, `hash`) y `SesionRepositoryPort` (`abrir`, `rotar`,
  `revocarSesionDe`).
- [x] 4.2 `RefreshTokenGeneratorAdapter` (`SecureRandom`, 32 bytes, base64url sin relleno; SHA-256
  hex). Verificar con `RefreshTokenGeneratorAdapterTest`: longitud, alfabeto, dos valores distintos,
  hash de 64 hex determinista y distinto del valor.
- [x] 4.3 `LoginUseCase` devuelve `SessionTokens`: emite el access con la versión, genera el refresh,
  abre la familia con `expires_at = ahora + refresh-expiration` y guarda solo el hash. Constructor
  explícito por el `@Value`. Verificar con `LoginUseCaseTest` (reloj fijo; lo guardado es el hash; no
  abre sesión con credenciales inválidas).
- [x] 4.4 `spring.security.jwt.refresh-expiration: ${JWT_REFRESH_EXPIRATION:24h}` en
  `application.yaml`, `24h` en el de test, y su comprobación en `CloudRunConfigTest`.
- [x] 4.5 `LoginResponse` pasa a `TokenResponse` con `refreshToken` y `refreshExpiresIn`. Verificar
  con `AuthControllerTest` (forma del login).
- [x] 4.6 `SesionR2dbcAdapter.abrir`. Lo verifica Bruno (`login.yml`).

## 5. Refresco y logout

Skills: `java-architect`, `java-security`, `java-exceptions`.

- [x] 5.1 `RefreshSessionPort` y `RefreshSessionUseCase`: hashea, `rotar`; con resultado emite access
  y refresh nuevos con el vencimiento de la familia y `refreshExpiresIn = expires_at - ahora`; vacío
  → `revocarSesionDe` y 401 `UNAUTHENTICATED` en `refreshToken`. Verificar con
  `RefreshSessionUseCaseTest` (rotación correcta, vencimiento conservado, rechazo revoca la familia y
  no emite).
- [x] 5.2 `LogoutPort` y `LogoutUseCase`: hashea y revoca la familia; completa vacío exista o no.
  Verificar con `LogoutUseCaseTest`.
- [x] 5.3 `SesionR2dbcAdapter.rotar` (CTE `UPDATE ... RETURNING` + `INSERT`, decisión 4) y
  `revocarSesionDe`. Lo verifica Bruno (2.1).
- [x] 5.4 `RefreshTokenRequest` con `@NotBlank`; `AuthController` con `POST /auth/refresh` (200) y
  `POST /auth/logout` (204). Verificar con `AuthControllerTest`: forma del refresco, 204 del logout,
  400 en `refreshToken` de cada uno y 401 del caso de uso con su campo.
- [x] 5.5 `RUTAS_BASIC` suma `POST /auth/refresh` y `POST /auth/logout`. Verificar con
  `JwtSecurityIT`: sin credencial y con Bearer válido dan 401 con `WWW-Authenticate: Basic`.
- [x] 5.6 Correr `bruno/auth` y `bruno/users` (`verificar-bruno.ps1 -Objetivo ... -RecargarDatos`):
  todo lo del grupo 2 en verde.

## 6. Documentación y colecciones

- [x] 6.1 `docs/api/contrato-api.md`: índice, *Bearer* (sin «No hay refresh token»; la sesión de 24
  h, la consulta de la sesión y la caída de sesiones al desplegar), el login ampliado, `POST
  /api/auth/refresh`, `POST /api/auth/logout`, el cambio de contraseña que cierra sesiones y *El 401*
  con el caso de `refreshToken`.
- [x] 6.2 `AGENTS.md`: las rutas de la cadena Basic, y en *Configuración y arranque* que el decoder
  consulta la sesión por un puerto de `compartido` que implementa `core`.
- [x] 6.3 `bruno-personal/`: el login guarda el refresh, y requests de refresco y logout con la misma
  ruta, cuerpo y Basic que `bruno/`. Sin ejecutar: apunta a Neon.
- [x] 6.4 Issue nuevo en Jira, `pendiente`, componente `Backend`, epic FIN-6: limpiar las filas
  vencidas de `refresh_tokens`, surgido en FIN-47. Creado: FIN-51.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea.

  `clean build`: 1246 tests, 0 fallos, 0 omitidos; cobertura de línea 98,61 %.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests y asserts reales, y `check-saldos.sql` sin descuadres.

  630 requests (630 pasan), 504/504 tests y 1211/1211 asserts; `check-saldos.sql`: las 60 cuentas
  cuadran.
