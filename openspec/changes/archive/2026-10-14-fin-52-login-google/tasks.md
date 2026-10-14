# Tasks

## 1. Esquema

- [x] 1.1 `docs/database/update/20261014_01_login_google.sql`:
  - `password_hash` sin `NOT NULL`;
  - columna `google_sub VARCHAR(255)`;
  - `ux_users_google_sub` parcial y `ck_users_metodo` (decisión 6 de design.md);
  - comentarios de columna y bloque de reversión comentado.

  El encabezado dice que va **antes** del despliegue y que reinicia los datos locales
  (`cargar-datos-local.sql`). Verificar aplicándolo a la base local con `psql -v ON_ERROR_STOP=1` y
  recargando los datos.
- [x] 1.2 Lo mismo en `schema.sql` (y el `COMMENT ON TABLE` de `users`, que hoy dice que la
  autenticación es solo email + hash) y en `docs/database/modelo-datos.md`. Verificar con la
  comparación de esquemas de ese documento: sin diferencias.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 `.claude/scripts/verificar-bruno.ps1` arranca la app con `GOOGLE_PROVEEDOR=stub`, junto a
  los otros dos stubs, y `AGENTS.md` lo dice donde nombra a los otros dos.
- [x] 2.2 Carpeta `bruno/auth-google/`, con la Basic en `folder.yml` como `bruno/auth/`. Tokens del
  stub `stub|<sub>|<email>|<nombre>|<apellido>`, con un `Date.now()` por corrida para que se pueda
  repetir sin recargar. Requests, en orden:
  - `registro.yml`: 201 con la forma del login. Guarda `accessGoogle`, `refreshGoogle`, `subGoogle`
    y `emailGoogle`;
  - `registro-perfil.yml`: `GET /users/me` con `accessGoogle` trae el correo, el nombre, el apellido,
    `Europe/Madrid` y `COP`;
  - `registro-categorias.yml`: `GET /categories` no vacío;
  - `registro-sin-zona.yml` (otro `sub` y otro correo): el perfil queda en `America/Bogota`;
  - `registro-sin-nombre.yml`: con nombre y apellido vacíos en el token, el nombre del perfil es la
    parte del correo anterior a la `@`;
  - `registro-duplicado.yml`: el mismo token, 409 `DUPLICATE_RESOURCE` en `email`;
  - `vincular-alta.yml`: `POST /auth/register` con contraseña de un `vincular-<ts>@bruno.local`, que
    usan este request y los de vinculación;
  - `registro-correo-con-clave.yml`: Google con ese correo y otro `sub`, 409 `DUPLICATE_RESOURCE` en
    `email`;
  - `registro-no-admitido.yml`: 403 `REGISTRATION_NOT_ALLOWED` en `email`;
  - `registro-token-invalido.yml`: 401 `INVALID_CREDENTIALS` en `idToken`;
  - `registro-sin-cuerpo.yml`: 400 `VALIDATION_ERROR` en `idToken`;
  - `registro-zona-invalida.yml`: 400 `VALIDATION_ERROR` en `timezone`;
  - `registro-con-bearer.yml`: 401 en `authorization`;
  - `login.yml`: 200 con la forma del login;
  - `login-sin-cuenta.yml`: 404 `ACCOUNT_NOT_REGISTERED` en `idToken`;
  - `login-inactivo.yml`: con el correo de `inactivo@financeapp.local`, 404;
  - `login-token-invalido.yml`: 401 `INVALID_CREDENTIALS` en `idToken`;
  - `login-sin-cuerpo.yml`: 400 en `idToken`;
  - `login-sin-credencial.yml`: 401 en `authorization` con `WWW-Authenticate: Basic`;
  - `refresh.yml` y `logout.yml` sobre la sesión de Google, y `refresh-tras-logout.yml`: 401;
  - `login-con-clave-sin-clave.yml`: `POST /auth/login` con `emailGoogle` y cualquier contraseña, 401
    `INVALID_CREDENTIALS` en `credentials`;
  - `cambiar-clave-sin-clave.yml` y `cambiar-correo-sin-clave.yml`: con `accessGoogle`, 400
    `INVALID_CREDENTIALS` en `currentPassword`, y un `GET /users/me` después sigue en 200 con el
    correo de antes;
  - vinculación, con el usuario de `vincular-alta.yml`: `vincular-login.yml` entra con Google con
    ese correo (200); `vincular-login-clave.yml` entra con
    la contraseña (200); `vincular-otro-google.yml` usa otro `sub` con el mismo correo (409
    `DUPLICATE_RESOURCE` en `email`);
  - `vincular-cambia-correo.yml` cambia el correo con la contraseña, y `vincular-login-tras-cambio.yml`
    entra con el token del primer `sub`, que trae el correo anterior: 200, y su access abre el perfil
    con el correo nuevo.

  `docs` de `folder.yml`: que el stub solo existe fuera de `prod`, el formato del token, y que la
  firma, `aud`, el vencimiento y `email_verified` los cubre el test del adapter real.

  Hecho con dos requests más que el plan no listaba: `registro-sin-zona-perfil.yml` y
  `registro-sin-nombre-perfil.yml` leen el perfil, porque un request no puede registrar y leer a la
  vez. `perfil-tras-intentos.yml` es el `GET /users/me` posterior a los dos rechazos, y
  `vincular-perfil-tras-cambio.yml` el del último login. En total, 34 requests. `subGoogle` no hizo
  falta: el login compara el `sub` del access con el id que guarda `registro-perfil.yml`.
  `opencollection.yml` nombra la carpeta entre las que van con Basic.
- [x] 2.3 Verificar que fallan por la razón correcta: con el esquema del grupo 1 y la app sin el
  código nuevo, `verificar-bruno.ps1 -Objetivo auth-google -RecargarDatos`. Anotar aquí qué pasa y
  qué falla.

  34 requests: 29 fallan y 5 pasan, con 17 de 80 asserts. Las rutas `/auth/google/*` no existen,
  así que caen en la cadena Bearer y responden 401 con `WWW-Authenticate: Bearer`, y sin sesión de
  Google también fallan con 401 los requests que leen el perfil. Pasan `vincular-alta` y
  `vincular-login-clave`, que usan el alta y el login normales, y tres que pasan sin probar nada
  todavía: `logout` (204 con un refresh vacío), `refresh-tras-logout` (401 con ese refresh vacío) y
  `login-con-clave-sin-clave` (401, porque el usuario de Google no llegó a existir). Saldos
  cuadrados.

## 3. Verificación del ID token

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 Dominio: `GoogleIdentity(sub, email, firstName, lastName)` y
  `GoogleIdTokenVerifierPort.verificar(String)`, que emite `Mono<GoogleIdentity>`.
- [x] 3.2 `GoogleIdTokenVerifierAdapter` (`@ConditionalOnProperty google.proveedor=google`) con el
  decoder de la decisión 1, y la extracción de la identidad, el nombre y el 502 de la decisión 2.
  Verificar con `GoogleIdTokenVerifierAdapterTest`, contra un `HttpServer` propio que sirve un JWKS
  con una llave RSA generada en el test:
  - token válido → identidad con el correo en minúsculas;
  - firma de otra llave, `iss` ajeno, `aud` de otro client ID, vencido, `email_verified` falso o
    `"false"`, y texto que no es un JWT → vacío;
  - los dos `iss` de Google aceptados;
  - nombre de `given_name`, de `name` y de la parte del correo, recortado a 100;
  - JWKS que responde 500 o servidor caído → 502 `EXTERNAL_SERVICE_ERROR` en `idToken`.
- [x] 3.3 `StubGoogleIdTokenVerifierAdapter` (`@Profile("!prod")`, `google.proveedor=stub`) de la
  decisión 3. Verificar con `StubGoogleIdTokenVerifierAdapterTest`: formato válido, apellido vacío y
  texto que no empieza por `stub|` o al que le faltan partes.
- [x] 3.4 Configuración:
  - el bloque `google` (`proveedor`, `client-id` sin default, `jwks-uri`, `timeout`) en
    `application.yaml` y en el de test;
  - `GOOGLE_CLIENT_ID` en `CloudRunConfigTest`: resuelve, y sin la variable no arranca;
  - la clave en `application-local.yaml` y `application-prod.yaml`, que no se versionan;
  - en `AGENTS.md`, la variable en la lista sin default y la clave en el bloque del perfil local;
  - la variable en `docs/despliegue.md`, como pendiente de crear en el servicio.

  Verificar con `CloudRunConfigTest`.

## 4. Registro y login con Google

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `ErrorCodes.ACCOUNT_NOT_REGISTERED`.
- [x] 4.2 `AperturaDeSesion` (decisión 8) con lo que hoy hace `LoginUseCase.abrirSesion`.
  `LoginUseCase` lo usa y descarta un hash nulo (decisión 7). Verificar con `AperturaDeSesionTest` y
  con `LoginUseCaseTest`, que sigue en verde y suma el caso «usuario sin contraseña → 401
  `INVALID_CREDENTIALS`, sin llamar al hasher».
- [x] 4.3 `User` y `UserCredentials` llevan `googleSub`. `UserRepositoryPort` y `UserR2dbcAdapter`
  suman:
  - `findActiveByGoogleSub`;
  - `existsByGoogleSub` (entre los no borrados);
  - `linkGoogle(id, sub)`, que emite si vinculó, y un choque con el único sale como el 409;
  - `google_sub` en el `INSERT` y en las credenciales;
  - `coalesce(password_hash, '')` en `HASH_ACTIVO`.

  Los usos existentes de los dos records se actualizan. Se verifica con Bruno, grupo 2.
- [x] 4.4 `GoogleRegisterPort` y `GoogleRegisterUseCase`, con el orden de la decisión 5. Verificar
  con `GoogleRegisterUseCaseTest`:
  - camino feliz (usuario sin hash, con `sub`, nombre, apellido, zona y `COP`; sesión abierta);
  - zona por defecto;
  - token inválido → 401;
  - no admitido → 403, sin consultar si existe;
  - correo existente → 409;
  - `sub` existente → 409;
  - restricción apagada.
- [x] 4.5 `GoogleLoginPort` y `GoogleLoginUseCase`, con el orden de la decisión 5. Verificar con
  `GoogleLoginUseCaseTest`:
  - por `sub`;
  - vinculación por correo;
  - vinculación perdida en carrera → 409;
  - correo con otro `sub` → 409;
  - sin cuenta → 404;
  - token inválido → 401;
  - error del verificador propagado.
- [x] 4.6 `POST /auth/google/register` (201) y `POST /auth/google/login` (200) en `AuthController`,
  con `GoogleRegisterRequest` (`idToken` `@NotBlank`, `timezone` `@ZonaIana`) y `GoogleLoginRequest`.
  Las dos rutas van en `RUTAS_BASIC` de `SecurityConfig`. Verificar con `AuthControllerTest`:
  - 201 y 200 con la forma del login;
  - 400 en `idToken` y en `timezone`;
  - los errores del caso de uso con su status.

  Y con `JwtSecurityIT`: sin credencial y con Bearer, 401 en las dos rutas.
- [x] 4.7 `docs/api/contrato-api.md`:
  - las rutas en *Autenticación*;
  - las dos secciones nuevas, con cuerpo, respuesta, errores en orden y cómo obtener el ID token con
    Google Identity Services;
  - el login con contraseña de un usuario sin contraseña;
  - el comportamiento provisional del cambio de contraseña y de correo;
  - `ACCOUNT_NOT_REGISTERED` en *Errores*.

  `AGENTS.md`: las rutas de la cadena Basic. Verificar releyendo el contrato contra la spec.
- [x] 4.8 Replicar en `bruno-personal/auth/` el registro y el login con Google (ruta, cuerpo, Basic)
  con un `idToken` que el usuario pega a mano, sin ejecutarlos: esa colección apunta a Neon y usa
  Google real.

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura de línea
  ≥ 85 %.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde: la
  colección entera, ningún request omitido y saldos cuadrados, con los conteos reales.
